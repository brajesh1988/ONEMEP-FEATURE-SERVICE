package com.netlink.onemep_feature.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request/response payloads for a project's delivery schedule (ONEMEP-15). */
public final class DeliveryScheduleDto {
  private DeliveryScheduleDto() {}

  public record CreateRequest(
      @NotBlank(message = "Milestone is required.")
          @Size(max = 200, message = "Milestone cannot exceed 200 characters.")
          String milestone,
      @Size(max = 500, message = "Deliverable cannot exceed 500 characters.") String deliverable,
      LocalDate plannedDate,
      LocalDate actualDate,
      String status,
      @Size(max = 1000, message = "Remarks cannot exceed 1000 characters.") String remarks) {}

  public record UpdateRequest(
      @NotBlank(message = "Milestone is required.")
          @Size(max = 200, message = "Milestone cannot exceed 200 characters.")
          String milestone,
      @Size(max = 500, message = "Deliverable cannot exceed 500 characters.") String deliverable,
      LocalDate plannedDate,
      LocalDate actualDate,
      String status,
      @Size(max = 1000, message = "Remarks cannot exceed 1000 characters.") String remarks) {}

  public record Response(
      Long id,
      String milestone,
      String deliverable,
      LocalDate plannedDate,
      LocalDate actualDate,
      String status,
      String remarks,
      Long updatedBy,
      LocalDateTime updatedDate) {}
}
