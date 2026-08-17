package com.netlink.onemep_feature.designimport.dto;

import com.netlink.onemep_feature.designimport.model.ImportStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for the bulk Design importer (ONEMEP-35). */
public final class DesignImportDto {
  private DesignImportDto() {}

  /**
   * What POST returns. Deliberately thin: the work has not started, so there is nothing to report
   * except where to look for it.
   */
  public record Accepted(Long batchId, int fileCount, String statusUrl) {}

  /** One rejected row, as the correction list shows it. */
  public record RowError(int rowNumber, String column, String message) {}

  /** One spreadsheet's progress and outcome. */
  public record FileStatus(
      Long fileId,
      String filename,
      ImportStatus status,
      String statusLabel,
      long sizeBytes,
      int totalRows,
      int importedRows,
      int failedRows,
      String message,
      boolean errorsTruncated,
      List<RowError> errors) {}

  /**
   * The batch as a whole.
   *
   * @param summary the sentence ONEMEP-35 shows the user, e.g. "42 of 50 Designs imported. 8 rows
   *     require correction."
   */
  public record BatchStatus(
      Long batchId,
      Long projectId,
      ImportStatus status,
      String statusLabel,
      String summary,
      int totalFiles,
      int totalRows,
      int importedRows,
      int failedRows,
      LocalDateTime submittedAt,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      List<FileStatus> files) {}
}
