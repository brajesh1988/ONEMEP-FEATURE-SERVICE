package com.netlink.onemep_feature.project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for a project's stakeholder directory (ONEMEP-15). */
public final class StakeholderDto {
  private StakeholderDto() {}

  public record CreateRequest(
      @NotBlank(message = "Role is required.") String role,
      @NotBlank(message = "Name is required.")
          @Size(max = 150, message = "Name cannot exceed 150 characters.")
          String name,
      @Size(max = 150, message = "Organization cannot exceed 150 characters.") String organization,
      @Email(message = "Email must be valid.")
          @Size(max = 150, message = "Email cannot exceed 150 characters.")
          String email,
      @Size(max = 50, message = "Phone cannot exceed 50 characters.") String phone) {}

  public record UpdateRequest(
      @NotBlank(message = "Role is required.") String role,
      @NotBlank(message = "Name is required.")
          @Size(max = 150, message = "Name cannot exceed 150 characters.")
          String name,
      @Size(max = 150, message = "Organization cannot exceed 150 characters.") String organization,
      @Email(message = "Email must be valid.")
          @Size(max = 150, message = "Email cannot exceed 150 characters.")
          String email,
      @Size(max = 50, message = "Phone cannot exceed 50 characters.") String phone) {}

  public record Response(
      Long id,
      String role,
      String name,
      String organization,
      String email,
      String phone,
      Long updatedBy,
      LocalDateTime updatedDate) {}
}
