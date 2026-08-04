package com.netlink.onemep_feature.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payloads for the DID tab (Technical Master → DID): Design Intent & Brief, Delivery Schedule,
 * Client Information, Architect Team, Structure Consultant Team.
 */
public final class DidSpecificationDto {
  private DidSpecificationDto() {}

  // Leading "^$|" lets a blank contact number through — unlike @Email, @Pattern does not
  // treat an empty string as automatically valid, and the frontend sends "" (not null) for
  // fields the user left blank.
  private static final String PHONE_PATTERN = "^$|^[+]?[0-9()\\-\\s]{7,20}$";

  // ── Design Intent & Brief ────────────────────────────────────────────────────

  public record DesignIntentBrief(
      @NotBlank(message = "Design brief is required.")
          @Size(max = 1500, message = "Design brief cannot exceed 1500 characters.")
          String lockedDesignIntent,
      @Size(max = 2000, message = "Initial client RFI response cannot exceed 2000 characters.")
          String initialClientRfiResponse,
      String greenRatingTarget,
      @Size(max = 2000, message = "Sustainability mandates cannot exceed 2000 characters.")
          String sustainabilityMandates) {}

  // ── Delivery Schedule ─────────────────────────────────────────────────────────

  public record DeliveryStage(
      Long id,
      @Size(max = 50, message = "Stage name cannot exceed 50 characters.") String stageName,
      LocalDate startDate,
      LocalDate endDate) {}

  // ── Contacts (shared shape across Client / Architect / Structure) ─────────────

  public record ContactRow(
      Long id,
      @Size(max = 100, message = "Designation cannot exceed 100 characters.") String designation,
      @Size(max = 100, message = "Name cannot exceed 100 characters.") String name,
      @Email(message = "Mail ID must be a valid email address.") String mailId,
      @Pattern(regexp = PHONE_PATTERN, message = "Contact number must be a valid phone number.")
          String contactNo,
      boolean isDefault) {}

  // ── Client Information ───────────────────────────────────────────────────────

  public record ClientInformation(
      @Size(max = 200, message = "Client name cannot exceed 200 characters.") String clientName,
      @Size(max = 200, message = "Client company cannot exceed 200 characters.")
          String clientCompany,
      @Valid List<ContactRow> contacts) {}

  // ── Architect Team ───────────────────────────────────────────────────────────

  public record ArchitectTeam(
      @Size(max = 200, message = "Architecture firm cannot exceed 200 characters.")
          String architectureFirm,
      @Valid List<ContactRow> contacts) {}

  // ── Structure Consultant Team ────────────────────────────────────────────────

  public record StructureConsultantTeam(
      @Size(max = 200, message = "Structural consultancy cannot exceed 200 characters.")
          String structuralConsultancy,
      @Valid List<ContactRow> contacts) {}

  // ── Save request ─────────────────────────────────────────────────────────────

  public record UpsertRequest(
      @Valid DesignIntentBrief designIntentBrief,
      @Valid List<DeliveryStage> deliverySchedule,
      @Valid ClientInformation clientInformation,
      @Valid ArchitectTeam architectTeam,
      @Valid StructureConsultantTeam structureConsultantTeam) {}

  // ── Response ─────────────────────────────────────────────────────────────────

  public record Response(
      boolean exists,
      Long projectId,
      Long technicalMasterId,
      DesignIntentBrief designIntentBrief,
      List<DeliveryStage> deliverySchedule,
      ClientInformation clientInformation,
      ArchitectTeam architectTeam,
      StructureConsultantTeam structureConsultantTeam,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record GreenRatingOption(String code, String label) {}
}
