package com.netlink.onemep_feature.handlingoffice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Handling Office master. */
public final class HandlingOfficeDto {
  private HandlingOfficeDto() {}

  public record CreateRequest(
      @NotBlank(message = "Handling office name is required.")
          @Size(max = 150, message = "Handling office name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Handling office name is required.")
          @Size(max = 150, message = "Handling office name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record Response(
      Long id, String name, Boolean active, Long updatedBy, LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name) {}
}
