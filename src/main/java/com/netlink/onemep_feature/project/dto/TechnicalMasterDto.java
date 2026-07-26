package com.netlink.onemep_feature.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request/response payloads for the project-level Technical Master (ONEMEP-29).
 *
 * <p>Delivery schedule and client information are surfaced read-only from the project's own data;
 * they are not writable through this form.
 */
public final class TechnicalMasterDto {
  private TechnicalMasterDto() {}

  // ── Requests ────────────────────────────────────────────────────────────────

  /** Create-or-replace the whole Technical Master for a project. */
  public record UpsertRequest(
      @Size(max = 2000, message = "Remarks cannot exceed 2000 characters.") String remarks,
      @Valid List<ParameterRequest> parameters,
      @Valid List<DidRequest> didSpecifications) {}

  public record ParameterRequest(
      @NotBlank(message = "Parameter scope is required.") String scope,
      @NotNull(message = "Parameter technicalFieldId is required.") Long technicalFieldId,
      Long unitId,
      @Size(max = 500, message = "Parameter value cannot exceed 500 characters.") String value,
      @Size(max = 1000, message = "Parameter remarks cannot exceed 1000 characters.")
          String remarks) {}

  public record DidRequest(
      @NotBlank(message = "DID name is required.")
          @Size(max = 200, message = "DID name cannot exceed 200 characters.")
          String name,
      @Size(max = 500, message = "DID specification cannot exceed 500 characters.")
          String specification,
      Long unitId,
      @Size(max = 1000, message = "DID remarks cannot exceed 1000 characters.") String remarks) {}

  // ── Responses ───────────────────────────────────────────────────────────────

  /**
   * Consolidated read model. When {@code exists} is {@code false} the project has no Technical
   * Master yet; the parameter/DID/attachment lists are empty but the read-only delivery schedule
   * and client info are still populated from the project.
   */
  public record Response(
      boolean exists,
      Long id,
      Long projectId,
      String remarks,
      Long version,
      Boolean active,
      List<ParameterResponse> commonParameters,
      List<ParameterResponse> categorySpecificParameters,
      List<DidResponse> didSpecifications,
      List<AttachmentMetadata> attachments,
      List<DeliveryScheduleDto.Response> deliverySchedule,
      ClientInfo clientInfo,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record ParameterResponse(
      Long id,
      String scope,
      Long technicalFieldId,
      String technicalFieldName,
      Long unitId,
      String unitSymbol,
      String value,
      String remarks) {}

  public record DidResponse(
      Long id, String name, String specification, Long unitId, String unitSymbol, String remarks) {}

  /** Read-only client information, sourced from the project record. */
  public record ClientInfo(String client, String location) {}

  /**
   * Compact, read-only summary of a saved Technical Master (ONEMEP-30). Shows key info + section
   * counts + version details (current version + audit); {@code editable} mirrors {@code exists}
   * (authorized users edit via the full-form PUT). Counts are primitives so they always serialize.
   */
  public record Summary(
      boolean exists,
      Long projectId,
      String remarks,
      Long version,
      long commonParameterCount,
      long categorySpecificParameterCount,
      long didSpecificationCount,
      long attachmentCount,
      ClientInfo clientInfo,
      boolean editable,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}

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
