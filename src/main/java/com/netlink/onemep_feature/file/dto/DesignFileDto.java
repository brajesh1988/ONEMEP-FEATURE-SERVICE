package com.netlink.onemep_feature.file.dto;

import com.netlink.onemep_feature.file.model.CommentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for uploaded files and their version history (ONEMEP-39). */
public final class DesignFileDto {
  private DesignFileDto() {}

  /** One row of the Uploaded Files table — a logical file, not a revision. */
  public record FileSummary(
      Long id,
      String displayName,
      String currentRevisionLabel,
      String fileExtension,
      long versionCount,
      long openCommentCount,
      LocalDateTime updatedDate) {}

  /** One entry of the expanded version history. */
  public record VersionView(
      Long id,
      String revisionLabel,
      boolean current,
      String originalFilename,
      String fileExtension,
      String contentType,
      long sizeBytes,
      String note,
      String uploadedBy,
      Long uploadedById,
      LocalDateTime uploadedAt,
      long commentCount) {}

  /**
   * Outcome of one file in a multi-file upload. ONEMEP-39 expects partial success to be reported
   * per file — "2 of 3 files uploaded successfully" — rather than the batch failing as a unit.
   */
  public record UploadResult(
      String filename, boolean uploaded, Long fileId, String revisionLabel, String error) {}

  public record UploadSummary(
      int requested, int uploaded, int failed, List<UploadResult> results) {}

  public record AddCommentRequest(
      @NotBlank(message = "Enter a comment.")
          @Size(max = 2000, message = "Comment must not exceed 2000 characters.")
          String body) {}

  public record UpdateCommentRequest(CommentStatus status) {}

  public record CommentView(
      Long id,
      String body,
      CommentStatus status,
      String author,
      Long authorId,
      LocalDateTime createdDate) {}

  /** Comments grouped under the revision they were raised against. */
  public record VersionComments(
      Long versionId, String revisionLabel, boolean current, List<CommentView> comments) {}

  /** A download that the client should follow rather than stream through this service. */
  public record DownloadLink(String url, String filename, long sizeBytes) {}
}
