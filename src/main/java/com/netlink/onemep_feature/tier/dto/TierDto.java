package com.netlink.onemep_feature.tier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Tier master (ONEMEP-16/17/18). */
public final class TierDto {
  private TierDto() {}

  public record CreateRequest(
      @NotBlank(message = "Tier name is required.")
          @Size(max = 150, message = "Tier name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Tier name is required.")
          @Size(max = 150, message = "Tier name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record Response(
      Long id, String name, Boolean active, Long updatedBy, LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name) {}
}
