package com.netlink.onemep_feature.technical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** PROVISIONAL request/response payloads for Technical Master (ONEMEP-29). */
public final class TechnicalDto {
  private TechnicalDto() {}

  public record CreateRequest(
      @NotBlank(message = "Name is required.")
          @Size(max = 200, message = "Name must not exceed 200 characters.")
          String name,
      Long unitId,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Name is required.")
          @Size(max = 200, message = "Name must not exceed 200 characters.")
          String name,
      Long unitId,
      Boolean active) {}

  public record Response(
      Long id,
      String name,
      Long unitId,
      String unitSymbol,
      Boolean active,
      Long updatedBy,
      LocalDateTime updatedDate) {}
}
