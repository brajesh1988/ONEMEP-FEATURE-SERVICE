package com.netlink.onemep_feature.design.dto;

import com.netlink.onemep_feature.design.model.DesignStatus;
import com.netlink.onemep_feature.design.model.WorkProgress;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Design Register (ONEMEP-35/36/37). */
public final class DesignDto {
  private DesignDto() {}

  /**
   * Zone is the only optional segment — blank becomes {@code XX}. The other five are catalogue
   * references and mandatory.
   */
  public record CreateRequest(
      @Size(max = 10, message = "Zone must not exceed 10 characters.") String zoneCode,
      @NotNull(message = "Discipline is required.") Long disciplineId,
      @NotNull(message = "Type is required.") Long typeId,
      @NotNull(message = "Subject is required.") Long subjectId,
      @NotNull(message = "Floor is required.") Long floorId,
      @NotNull(message = "Stage is required.") Long stageId,
      String title,
      String sheetSize,
      String scale,
      String preparedBy,
      WorkProgress workProgress) {}

  /**
   * Only the descriptive fields. The segments and the Design Number are absent by design — they are
   * locked after creation (ONEMEP-37), and a payload that cannot express a change cannot smuggle
   * one through.
   */
  public record UpdateRequest(
      String title, String sheetSize, String scale, String preparedBy, WorkProgress workProgress) {}

  /** A resolved catalogue value as shown on the Design screens. */
  public record SegmentView(Long id, String code, String label) {}

  /** One row of the Design Register grid (ONEMEP-35). */
  public record ListItem(
      Long id,
      String designNumber,
      String title,
      String disciplineCode,
      String stageCode,
      long documentCount,
      DesignStatus status,
      WorkProgress workProgress,
      LocalDateTime updatedDate) {}

  /** Full record for the Detail and Edit screens. */
  public record Response(
      Long id,
      Long projectId,
      String projectCode,
      String designNumber,
      String zoneCode,
      SegmentView discipline,
      SegmentView type,
      SegmentView subject,
      SegmentView floor,
      SegmentView stage,
      String title,
      String sheetSize,
      String scale,
      String preparedBy,
      WorkProgress workProgress,
      DesignStatus status,
      DesignTaskDto.View task,
      Integer version,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  /** Live preview of the number the current segment selection would produce (ONEMEP-36). */
  public record NumberPreview(String designNumber) {}
}
