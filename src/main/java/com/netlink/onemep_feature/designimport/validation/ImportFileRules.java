package com.netlink.onemep_feature.designimport.validation;

import com.netlink.onemep_feature.designimport.parser.SpreadsheetParsers;
import com.netlink.onemep_feature.exception.ApplicationException;
import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;

/**
 * File-level checks applied at submission, before anything is queued (ONEMEP-35).
 *
 * <p>Only what can be known without reading the bytes. Everything that requires opening the file —
 * whether it is really a workbook, whether its header is right — belongs to the background pass,
 * where it fails one file instead of rejecting the whole submission.
 *
 * <p>These are rejections rather than per-file errors precisely because they are cheap and certain:
 * telling someone at upload time that they picked a {@code .docx} is better than accepting it and
 * making them poll for the disappointment.
 */
public final class ImportFileRules {
  private ImportFileRules() {}

  /** ONEMEP-35's ceiling, per file. Matches the service's configured multipart limit. */
  public static final long MAX_FILE_SIZE_BYTES = 150L * 1024 * 1024;

  /**
   * How many spreadsheets one batch may carry. Bounded because the request size limit has to
   * accommodate the worst case, and an unbounded batch has no worst case.
   */
  public static final int MAX_FILES_PER_BATCH = 4;

  public static void validateBatch(java.util.List<MultipartFile> files) {
    if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
      throw new ApplicationException("Choose one or more spreadsheets to import.");
    }
    if (files.size() > MAX_FILES_PER_BATCH) {
      throw new ApplicationException(
          "A maximum of " + MAX_FILES_PER_BATCH + " files can be imported at once.");
    }
    files.forEach(ImportFileRules::validate);
  }

  public static void validate(MultipartFile file) {
    String name = originalFilename(file);

    if (file.isEmpty() || file.getSize() <= 0) {
      throw new ApplicationException(name + " is empty and cannot be imported.");
    }
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new ApplicationException(name + " exceeds the maximum file size of 150 MB.");
    }
    if (!SpreadsheetParsers.isSupported(extensionOf(name))) {
      throw new ApplicationException(
          name + " is not a supported file type. Import an .xlsx or .csv file.");
    }
  }

  /** Leaf name only — some clients send a full path, and it is never trusted as one. */
  public static String originalFilename(MultipartFile file) {
    String raw = file.getOriginalFilename();
    if (raw == null || raw.isBlank()) {
      return "(unnamed file)";
    }
    String leaf = raw.replace('\\', '/');
    int slash = leaf.lastIndexOf('/');
    return slash >= 0 ? leaf.substring(slash + 1) : leaf;
  }

  public static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
