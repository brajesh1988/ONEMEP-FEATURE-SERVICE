package com.netlink.onemep_feature.project.dto;

import java.time.LocalDateTime;

/** Response payloads for project specs-sheet attachments (ONEMEP-15). */
public final class SpecSheetDto {
  private SpecSheetDto() {}

  /** Metadata view — never carries the file bytes. */
  public record Metadata(
      Long id,
      String fileName,
      String contentType,
      String fileExtension,
      Long fileSize,
      Long uploadedBy,
      LocalDateTime uploadedDate) {}
}
