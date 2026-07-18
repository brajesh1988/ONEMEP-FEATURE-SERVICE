package com.netlink.onemep_feature.detailinglevel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Detailing Level master. */
public final class DetailingLevelDto {
  private DetailingLevelDto() {}

  public record CreateRequest(
      @NotBlank(message = "Detailing level name is required.")
          @Size(max = 150, message = "Detailing level name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Detailing level name is required.")
          @Size(max = 150, message = "Detailing level name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record Response(
      Long id, String name, Boolean active, Long updatedBy, LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name) {}
}
