package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.storage.FileStorage;
import com.netlink.onemep_feature.common.storage.StorageKey;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.designimport.dto.DesignImportDto;
import com.netlink.onemep_feature.designimport.model.DesignImportBatch;
import com.netlink.onemep_feature.designimport.model.DesignImportFile;
import com.netlink.onemep_feature.designimport.model.ImportStatus;
import com.netlink.onemep_feature.designimport.repo.DesignImportBatchRepo;
import com.netlink.onemep_feature.designimport.repo.DesignImportFileRepo;
import com.netlink.onemep_feature.designimport.repo.DesignImportRowErrorRepo;
import com.netlink.onemep_feature.designimport.validation.ImportFileRules;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts import batches and reports on them (ONEMEP-35).
 *
 * <p>{@link #submit} is not {@code @Transactional}, for the reason {@code DesignFileServiceImpl}
 * already documents: the bytes must be written <em>outside</em> a transaction. Up to 600 MB of
 * spreadsheets streaming through a PUT while holding a Hikari connection open is a straightforward
 * way to starve the pool. It runs as three steps instead:
 *
 * <ol>
 *   <li><b>allocate</b> — short transaction: create the batch and one row per file, status READY;
 *   <li><b>store</b> — no transaction: write each file to object storage;
 *   <li><b>dispatch</b> — short transaction to record the keys, then hand the batch to the
 *       background executor.
 * </ol>
 *
 * <p>Dispatch happens strictly after the allocating transaction has committed. Handing the batch id
 * to another thread while the rows were still uncommitted would be a race the processor loses about
 * as often as it wins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignImportServiceImpl implements DesignImportService {

  private final ProjectRepo projectRepo;
  private final DesignImportBatchRepo batchRepo;
  private final DesignImportFileRepo fileRepo;
  private final DesignImportRowErrorRepo rowErrorRepo;
  private final DesignImportProcessor processor;
  private final FileStorage fileStorage;
  private final ApiResponseAdaptor apiResponseAdaptor;
  private final TransactionTemplate transactionTemplate;

  // ── submit ────────────────────────────────────────────────────────────────

  @Override
  public ApiResponse<?> submit(Long projectId, List<MultipartFile> files) {
    ImportFileRules.validateBatch(files);
    transactionTemplate.execute(status -> requireProject(projectId));

    Long batchId = transactionTemplate.execute(status -> allocate(projectId, files));

    try {
      storeAll(batchId, files);
    } catch (RuntimeException e) {
      // Nothing has been queued yet, so the batch is dead on arrival. Mark it rather than leaving a
      // READY row nothing will ever pick up.
      markBatchFailed(batchId, "The files could not be uploaded. Please try again.");
      throw e;
    }

    dispatch(batchId);

    return apiResponseAdaptor.success(
        files.size() == 1
            ? "1 file accepted for import."
            : files.size() + " files accepted for import.",
        new DesignImportDto.Accepted(batchId, files.size(), "/design-imports/" + batchId));
  }

  /** Step 1: the batch and its files, all READY, in one short transaction. */
  private Long allocate(Long projectId, List<MultipartFile> files) {
    ProjectMaster project = requireProject(projectId);

    DesignImportBatch batch = new DesignImportBatch();
    batch.setProject(project);
    batch.setStatus(ImportStatus.READY);
    batch.setTotalFiles(files.size());
    batch.setSummary(ImportSummary.inProgress());
    batch.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    DesignImportBatch savedBatch = batchRepo.save(batch);

    for (int index = 0; index < files.size(); index++) {
      MultipartFile file = files.get(index);
      String filename = ImportFileRules.originalFilename(file);

      DesignImportFile row = new DesignImportFile();
      row.setBatch(savedBatch);
      row.setOrdinal(index);
      row.setOriginalFilename(filename);
      row.setFileExtension(ImportFileRules.extensionOf(filename));
      row.setContentType(file.getContentType());
      row.setSizeBytes(file.getSize());
      row.setStatus(ImportStatus.READY);
      row.setCreatedBy(SecurityUtils.getUserId().orElse(null));
      fileRepo.save(row);
    }

    return savedBatch.getId();
  }

  /** Step 2: bytes to storage, outside any transaction; then step 3 records where they went. */
  private void storeAll(Long batchId, List<MultipartFile> files) {
    List<DesignImportFile> rows =
        transactionTemplate.execute(status -> fileRepo.findForBatch(batchId));
    if (rows == null) {
      throw new ApplicationException("The import could not be started. Please try again.");
    }

    List<StorageKey> written = new ArrayList<>();
    try {
      for (DesignImportFile row : rows) {
        // Matched by ordinal rather than list position: the rows come back ordered by it, and
        // pairing two lists by index only works for as long as nobody changes either query.
        MultipartFile file = files.get(row.getOrdinal());
        StorageKey key = keyFor(batchId, row.getId());

        setFileStatus(row.getId(), ImportStatus.UPLOADING);
        try (InputStream in = file.getInputStream()) {
          fileStorage.put(key, in, file.getSize(), file.getContentType());
        } catch (IOException e) {
          throw new ApplicationException(
              row.getOriginalFilename() + " could not be read. Verify the file and try again.");
        }
        written.add(key);
        recordKey(row.getId(), key);
      }
    } catch (RuntimeException e) {
      // Bytes with no batch behind them are litter; remove what did land before giving up.
      written.forEach(this::safeDelete);
      throw e;
    }
  }

  /**
   * Step 3. The executor's queue is bounded, so this can be refused — which is a real answer ("we
   * are saturated, try later"), not something to swallow.
   */
  private void dispatch(Long batchId) {
    try {
      processor.process(batchId);
    } catch (RejectedExecutionException e) {
      // TaskRejectedException extends this, so the one catch covers both the raw executor
      // rejection and Spring's wrapper around it.
      markBatchFailed(
          batchId, "The service is processing too many imports right now. Please try again later.");
      throw new ApplicationException(
          "The service is processing too many imports right now. Please try again later.");
    }
  }

  // ── status ────────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> status(Long batchId) {
    DesignImportBatch batch =
        batchRepo
            .findById(batchId)
            .orElseThrow(
                () -> new ResourceNotFoundException("This import is no longer available."));

    List<DesignImportDto.FileStatus> files =
        fileRepo.findForBatch(batchId).stream().map(this::toFileStatus).toList();

    DesignImportDto.BatchStatus status =
        new DesignImportDto.BatchStatus(
            batch.getId(),
            batch.getProject().getId(),
            batch.getStatus(),
            batch.getStatus().label(),
            batch.getSummary(),
            batch.getTotalFiles(),
            batch.getTotalRows(),
            batch.getImportedRows(),
            batch.getFailedRows(),
            batch.getCreatedDate(),
            batch.getStartedAt(),
            batch.getFinishedAt(),
            files);

    return apiResponseAdaptor.success(
        batch.getStatus().isTerminal()
            ? "Import status fetched successfully."
            : "Import is still running.",
        status);
  }

  private DesignImportDto.FileStatus toFileStatus(DesignImportFile file) {
    List<DesignImportDto.RowError> errors =
        rowErrorRepo.findForFile(file.getId()).stream()
            .map(
                error ->
                    new DesignImportDto.RowError(
                        error.getRowNumber(), error.getColumnName(), error.getMessage()))
            .toList();

    return new DesignImportDto.FileStatus(
        file.getId(),
        file.getOriginalFilename(),
        file.getStatus(),
        file.getStatus().label(),
        file.getSizeBytes(),
        file.getTotalRows(),
        file.getImportedRows(),
        file.getFailedRows(),
        file.getMessage(),
        Boolean.TRUE.equals(file.getErrorsTruncated()),
        errors);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Never derived from the uploaded filename — see {@link StorageKey}. */
  private static StorageKey keyFor(Long batchId, Long fileId) {
    return StorageKey.of("design-imports", batchId, fileId);
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private void setFileStatus(Long fileId, ImportStatus status) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          DesignImportFile file = fileRepo.getReferenceById(fileId);
          file.setStatus(status);
          fileRepo.save(file);
        });
  }

  private void recordKey(Long fileId, StorageKey key) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          DesignImportFile file = fileRepo.getReferenceById(fileId);
          file.setStorageKey(key.value());
          file.setStatus(ImportStatus.READY);
          fileRepo.save(file);
        });
  }

  private void markBatchFailed(Long batchId, String summary) {
    try {
      transactionTemplate.executeWithoutResult(
          tx -> {
            DesignImportBatch batch = batchRepo.getReferenceById(batchId);
            batch.setStatus(ImportStatus.FAILED);
            batch.setSummary(summary);
            batchRepo.save(batch);
          });
    } catch (RuntimeException e) {
      log.error("Could not mark design import batch {} as failed", batchId, e);
    }
  }

  /** Storage cleanup must never be the reason an operation reports a different failure. */
  private void safeDelete(StorageKey key) {
    try {
      fileStorage.delete(key);
    } catch (RuntimeException e) {
      log.error("Orphaned import object left in storage at key {}", key, e);
    }
  }
}
