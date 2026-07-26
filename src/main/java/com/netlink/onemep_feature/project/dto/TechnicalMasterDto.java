package com.netlink.onemep_feature.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payloads for the project-level Technical Master (ONEMEP-29), driven by the PTMS field catalog.
 *
 * <p>The form is served by the backend per the project's category ({@link Template}); the user
 * fills a value per field; values persist as a {@code fieldKey -> value} map. Delivery schedule and
 * client information are surfaced read-only from the project.
 */
public final class TechnicalMasterDto {
  private TechnicalMasterDto() {}

  // ── Template (per category, editable catalog) ───────────────────────────────

  /** The form for a project's category: ordered sections, each with its fields. */
  public record Template(Long projectId, Integer seriesCode, List<Section> sections) {}

  public record Section(
      Long id, String title, int order, boolean active, boolean deletable, List<Field> fields) {}

  public record Field(
      Long id,
      String key,
      String label,
      String unit,
      String dataType,
      boolean required,
      boolean active,
      String feeds,
      String notes) {}

  // ── Catalog edit requests (add head / field, per category) ──────────────────

  public record SectionRequest(
      @NotBlank(message = "Section title is required.")
          @Size(max = 120, message = "Section title cannot exceed 120 characters.")
          String title,
      Boolean active) {}

  public record FieldRequest(
      @NotNull(message = "Section is required.") Long sectionId,
      @NotBlank(message = "Field label is required.")
          @Size(max = 200, message = "Field label cannot exceed 200 characters.")
          String label,
      @Size(max = 60, message = "Unit cannot exceed 60 characters.") String unit,
      String dataType,
      Boolean required,
      Boolean active) {}

  // ── Value requests ──────────────────────────────────────────────────────────

  /** Create-or-replace: general remarks + the filled values (blank values are cleared). */
  public record UpsertRequest(
      @Size(max = 2000, message = "Remarks cannot exceed 2000 characters.") String remarks,
      Map<String, String> values) {}

  // ── Responses ───────────────────────────────────────────────────────────────

  public record Response(
      boolean exists,
      Long id,
      Long projectId,
      String remarks,
      Map<String, String> values,
      List<AttachmentMetadata> attachments,
      List<DeliveryScheduleDto.Response> deliverySchedule,
      ClientInfo clientInfo,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record Summary(
      boolean exists,
      Long projectId,
      String remarks,
      long totalFields,
      long filledFieldCount,
      long sectionCount,
      long attachmentCount,
      ClientInfo clientInfo,
      boolean editable,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  /** Read-only client information, sourced from the project record. */
  public record ClientInfo(String client, String location) {}

  /** Attachment metadata — never carries the file bytes. */
  public record AttachmentMetadata(
      Long id,
      String fileName,
      String contentType,
      String fileExtension,
      Long fileSize,
      Long uploadedBy,
      LocalDateTime uploadedDate) {}
}
