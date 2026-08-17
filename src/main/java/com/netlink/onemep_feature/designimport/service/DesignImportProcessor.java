package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.common.storage.FileStorage;
import com.netlink.onemep_feature.common.storage.StorageKey;
import com.netlink.onemep_feature.common.util.DateUtils;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.config.AsyncConfig;
import com.netlink.onemep_feature.designimport.model.DesignImportBatch;
import com.netlink.onemep_feature.designimport.model.DesignImportFile;
import com.netlink.onemep_feature.designimport.model.DesignImportRowError;
import com.netlink.onemep_feature.designimport.model.ImportStatus;
import com.netlink.onemep_feature.designimport.parser.ImportColumn;
import com.netlink.onemep_feature.designimport.parser.SheetRow;
import com.netlink.onemep_feature.designimport.parser.SpreadsheetFormatException;
import com.netlink.onemep_feature.designimport.parser.SpreadsheetParser;
import com.netlink.onemep_feature.designimport.parser.SpreadsheetParsers;
import com.netlink.onemep_feature.designimport.repo.DesignImportBatchRepo;
import com.netlink.onemep_feature.designimport.repo.DesignImportFileRepo;
import com.netlink.onemep_feature.designimport.repo.DesignImportRowErrorRepo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one submitted batch to completion on a background thread (ONEMEP-35).
 *
 * <p>Structured so that <b>nothing that can fail takes anything else down with it</b>, because
 * partial success is the required behaviour at every level: a bad row fails its row, an unreadable
 * file fails its file, and neither touches the rest of the batch. Only a failure of the batch
 * bookkeeping itself fails the batch.
 *
 * <p>Each file is handled as: mark PROCESSING → materialise the stored object as a local temporary
 * file → stream it row by row → write each valid row in its own transaction → record the outcome.
 * The temporary file exists because POI cannot stream an {@code .xlsx} from an {@code InputStream}
 * without first buffering the entire package in memory, which is exactly what must not happen at
 * this ceiling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DesignImportProcessor {

  /**
   * Rows retained per file. A spreadsheet with a hundred thousand rows is not a spreadsheet someone
   * is going to correct by hand, and the in-batch duplicate index grows with every row read.
   */
  static final int MAX_ROWS_PER_FILE = 50_000;

  /**
   * Errors retained per file. The true failure count is still reported; this caps only how many
   * individual messages are stored, so one catastrophically wrong file cannot fill the table.
   */
  static final int MAX_ERRORS_PER_FILE = 500;

  private final DesignImportBatchRepo batchRepo;
  private final DesignImportFileRepo fileRepo;
  private final DesignImportRowErrorRepo rowErrorRepo;
  private final DesignRowValidator rowValidator;
  private final DesignImportWriter writer;
  private final FileStorage fileStorage;
  private final TransactionTemplate transactionTemplate;

  /**
   * Entry point, invoked after the HTTP response has already gone out with 202.
   *
   * <p>Takes an id rather than an entity: the caller's persistence context is closed by the time
   * this runs, and everything here belongs to its own transactions.
   */
  @Async(AsyncConfig.IMPORT_EXECUTOR)
  public void process(Long batchId) {
    try {
      runBatch(batchId);
    } catch (RuntimeException e) {
      // Last line of defence. An exception escaping an @Async void method is logged by the
      // executor and otherwise vanishes, which would leave the batch stuck in PROCESSING forever
      // with no way for the user to learn that it is never coming back.
      log.error("Design import batch {} failed", batchId, e);
      failBatch(batchId);
    }
  }

  private void runBatch(Long batchId) {
    BatchContext context = transactionTemplate.execute(status -> startBatch(batchId));
    if (context == null) {
      return;
    }

    BatchDuplicateIndex seen = new BatchDuplicateIndex();
    int importedRows = 0;
    int totalRows = 0;

    for (Long fileId : context.fileIds()) {
      FileOutcome outcome = processFile(context, fileId, seen);
      importedRows += outcome.imported();
      totalRows += outcome.total();
    }

    finishBatch(batchId, totalRows, importedRows);
  }

  // ── one file ──────────────────────────────────────────────────────────────

  private FileOutcome processFile(BatchContext context, Long fileId, BatchDuplicateIndex seen) {
    FileContext file = transactionTemplate.execute(status -> beginFile(fileId));
    if (file == null) {
      return new FileOutcome(0, 0);
    }
    seen.enterFile(file.filename());

    Path spooled = null;
    try {
      spooled = spool(file);
      Collector collector = new Collector(context, file, seen);
      SpreadsheetParser parser = SpreadsheetParsers.forExtension(file.extension());
      parser.parse(spooled, collector);
      collector.requireRequiredColumns();

      completeFile(fileId, collector);
      return new FileOutcome(collector.imported, collector.considered);

    } catch (SpreadsheetFormatException e) {
      log.warn("Import file {} ({}) is unreadable: {}", fileId, file.filename(), e.getMessage());
      failFile(fileId, e.getMessage());
      return new FileOutcome(0, 0);
    } catch (RuntimeException e) {
      log.error("Import file {} ({}) failed unexpectedly", fileId, file.filename(), e);
      failFile(fileId, "The file could not be processed. Please try uploading it again.");
      return new FileOutcome(0, 0);
    } finally {
      deleteQuietly(spooled);
    }
  }

  /**
   * Copies the stored object to a local temporary file.
   *
   * <p>Not an optimisation: {@code OPCPackage.open(InputStream)} reads the whole package into the
   * heap before yielding a single row, so streaming an {@code .xlsx} at all requires a seekable
   * file. The copy is streamed, so peak memory is one buffer rather than one workbook.
   */
  private Path spool(FileContext file) {
    if (file.storageKey() == null) {
      throw new SpreadsheetFormatException("The uploaded file is no longer available.");
    }
    Path target;
    try {
      target = Files.createTempFile("design-import-", "." + file.extension());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create a temporary file for the import.", e);
    }
    try (InputStream in = fileStorage.open(new StorageKey(file.storageKey()))) {
      Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException e) {
      deleteQuietly(target);
      throw new SpreadsheetFormatException("The uploaded file could not be read from storage.", e);
    } catch (RuntimeException e) {
      deleteQuietly(target);
      throw e;
    }
  }

  /**
   * Receives rows from the parser and turns them into Designs or errors.
   *
   * <p>State lives here rather than in the processor so that one file's counters cannot leak into
   * the next, and so the whole per-file pass stays readable as a single object.
   */
  private final class Collector implements SpreadsheetParser.RowHandler {

    private final BatchContext context;
    private final FileContext file;
    private final BatchDuplicateIndex seen;
    private final List<RowProblem> retained = new ArrayList<>();

    private List<ImportColumn> missingColumns = List.of();
    private int considered;
    private int imported;
    private int failed;
    private boolean truncated;
    private boolean capped;

    Collector(BatchContext context, FileContext file, BatchDuplicateIndex seen) {
      this.context = context;
      this.file = file;
      this.seen = seen;
    }

    @Override
    public void header(Map<ImportColumn, Integer> headerMap, List<String> unrecognised) {
      missingColumns =
          ImportColumn.requiredColumns().stream()
              .filter(column -> !headerMap.containsKey(column))
              .toList();
      if (!unrecognised.isEmpty()) {
        log.debug("Import file {} carries unrecognised columns {}", file.filename(), unrecognised);
      }
    }

    @Override
    public boolean row(SheetRow row) {
      // A file missing a required column cannot produce a single valid Design, so reading the rest
      // of it would only generate one identical error per row.
      if (!missingColumns.isEmpty()) {
        return false;
      }
      if (considered >= MAX_ROWS_PER_FILE) {
        capped = true;
        return false;
      }

      RowValidationResult result =
          rowValidator.validate(context.projectId(), context.projectCode(), row, seen);

      if (result instanceof RowValidationResult.Skipped) {
        return true;
      }
      considered++;

      if (result instanceof RowValidationResult.Rejected rejected) {
        failed++;
        retain(rejected.problems());
        return true;
      }

      ValidatedRow valid = ((RowValidationResult.Valid) result).row();
      try {
        writer.write(context.projectId(), valid, file.filename());
        imported++;
        // Claimed only once the row is actually in the register, so a row that failed to write
        // cannot make a later, legitimate row look like a duplicate of something that isn't there.
        seen.claim(valid.rowNumber(), valid.designNumber(), valid.titleNormalized());
      } catch (RuntimeException e) {
        failed++;
        retain(List.of(RowProblem.at(valid.rowNumber(), writeFailureMessage(e))));
        log.warn("Import row {} of {} failed to write", valid.rowNumber(), file.filename(), e);
      }
      return true;
    }

    private void retain(List<RowProblem> problems) {
      for (RowProblem problem : problems) {
        if (retained.size() >= MAX_ERRORS_PER_FILE) {
          truncated = true;
          return;
        }
        retained.add(problem);
      }
    }

    /** A file whose header is wrong fails wholesale, with the columns named. */
    void requireRequiredColumns() {
      if (missingColumns.isEmpty()) {
        return;
      }
      String names =
          missingColumns.stream()
              .map(ImportColumn::header)
              .collect(java.util.stream.Collectors.joining(", "));
      throw new SpreadsheetFormatException(
          "The file is missing required column"
              + (missingColumns.size() == 1 ? " " : "s ")
              + names
              + ".");
    }

    String message() {
      if (capped) {
        return ImportSummary.of(imported, considered)
            + " Only the first "
            + MAX_ROWS_PER_FILE
            + " rows were read.";
      }
      return ImportSummary.of(imported, considered);
    }
  }

  /**
   * A concurrent insert can beat the pre-check and land on {@code uq_design_number} or {@code
   * uq_design_title}. The user should see the same duplicate wording either way, not a database
   * error.
   */
  private static String writeFailureMessage(RuntimeException e) {
    String detail = String.valueOf(e.getMessage());
    if (detail.contains("uq_design_number")) {
      return "This Design Number was created by someone else while the import was running.";
    }
    if (detail.contains("uq_design_title")) {
      return "A Design with this Title was created by someone else while the import was running.";
    }
    return "This row could not be saved. Please try again.";
  }

  // ── bookkeeping ───────────────────────────────────────────────────────────

  /** What the processor needs from the batch, read once so no entity is held across files. */
  private record BatchContext(
      Long batchId, Long projectId, String projectCode, List<Long> fileIds) {}

  private record FileContext(Long fileId, String filename, String extension, String storageKey) {}

  private record FileOutcome(int imported, int total) {}

  private BatchContext startBatch(Long batchId) {
    DesignImportBatch batch = batchRepo.findById(batchId).orElse(null);
    if (batch == null) {
      log.warn("Design import batch {} vanished before processing", batchId);
      return null;
    }
    batch.setStatus(ImportStatus.PROCESSING);
    batch.setStartedAt(DateUtils.getCurrentUtcTime());
    batch.setSummary(ImportSummary.inProgress());
    batch.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    batchRepo.save(batch);

    return new BatchContext(
        batchId,
        batch.getProject().getId(),
        batch.getProject().getProjectNumber(),
        fileRepo.findForBatch(batchId).stream().map(DesignImportFile::getId).toList());
  }

  private FileContext beginFile(Long fileId) {
    DesignImportFile file = fileRepo.findById(fileId).orElse(null);
    if (file == null) {
      return null;
    }
    file.setStatus(ImportStatus.PROCESSING);
    fileRepo.save(file);
    return new FileContext(
        fileId, file.getOriginalFilename(), file.getFileExtension(), file.getStorageKey());
  }

  private void completeFile(Long fileId, Collector collector) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DesignImportFile file = fileRepo.getReferenceById(fileId);
          file.setTotalRows(collector.considered);
          file.setImportedRows(collector.imported);
          file.setFailedRows(collector.failed);
          file.setErrorsTruncated(collector.truncated);
          file.setMessage(collector.message());
          file.setStatus(fileStatus(collector));
          fileRepo.save(file);

          collector.retained.forEach(problem -> rowErrorRepo.save(toEntity(file, problem)));
        });
  }

  /**
   * A file with no failures is Imported; one with some of each is Completed with errors; one where
   * nothing survived is Failed, because "completed" would overstate what happened.
   */
  private static ImportStatus fileStatus(Collector collector) {
    if (collector.failed == 0) {
      return ImportStatus.IMPORTED;
    }
    return collector.imported == 0 ? ImportStatus.FAILED : ImportStatus.COMPLETED_WITH_ERRORS;
  }

  private static DesignImportRowError toEntity(DesignImportFile file, RowProblem problem) {
    DesignImportRowError error = new DesignImportRowError();
    error.setFile(file);
    error.setRowNumber(problem.rowNumber());
    error.setColumnName(problem.columnName());
    error.setMessage(problem.message());
    error.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    return error;
  }

  private void failFile(Long fileId, String message) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DesignImportFile file = fileRepo.getReferenceById(fileId);
          file.setStatus(ImportStatus.FAILED);
          file.setMessage(message == null ? ImportSummary.unreadable() : message);
          fileRepo.save(file);
        });
  }

  private void finishBatch(Long batchId, int totalRows, int importedRows) {
    transactionTemplate.executeWithoutResult(
        status -> {
          DesignImportBatch batch = batchRepo.getReferenceById(batchId);
          List<DesignImportFile> files = fileRepo.findForBatch(batchId);

          boolean anyFileFailed =
              files.stream().anyMatch(file -> file.getStatus() != ImportStatus.IMPORTED);

          batch.setTotalRows(totalRows);
          batch.setImportedRows(importedRows);
          batch.setFailedRows(totalRows - importedRows);
          batch.setSummary(ImportSummary.of(importedRows, totalRows));
          batch.setStatus(batchStatus(importedRows, totalRows, anyFileFailed));
          batch.setFinishedAt(DateUtils.getCurrentUtcTime());
          batchRepo.save(batch);
        });
  }

  /**
   * The batch reflects its files, and must never contradict them: a batch whose every file came
   * back clean is IMPORTED even when there was nothing in them to import. Only once something has
   * actually gone wrong does zero imported rows mean FAILED — which is where a batch of entirely
   * unreadable files lands, alongside one where every row was rejected. Both leave the register
   * unchanged, and the per-file messages are what distinguish them.
   */
  private static ImportStatus batchStatus(int imported, int total, boolean anyFileFailed) {
    if (!anyFileFailed && imported == total) {
      return ImportStatus.IMPORTED;
    }
    return imported == 0 ? ImportStatus.FAILED : ImportStatus.COMPLETED_WITH_ERRORS;
  }

  private void failBatch(Long batchId) {
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            DesignImportBatch batch = batchRepo.getReferenceById(batchId);
            batch.setStatus(ImportStatus.FAILED);
            batch.setSummary("The import could not be completed. Please try again.");
            batch.setFinishedAt(DateUtils.getCurrentUtcTime());
            batchRepo.save(batch);
          });
    } catch (RuntimeException e) {
      log.error("Could not mark design import batch {} as failed", batchId, e);
    }
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not remove temporary import file {}", path, e);
    }
  }
}
