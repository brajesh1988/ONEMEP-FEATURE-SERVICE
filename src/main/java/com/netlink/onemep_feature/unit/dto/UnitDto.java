package com.netlink.onemep_feature.unit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Unit master (ONEMEP-26/27/28). */
public final class UnitDto {
  private UnitDto() {}

  public record CreateRequest(
      @NotBlank(message = "Unit name is required.")
          @Size(max = 150, message = "Unit name must not exceed 150 characters.")
          String name,
      @NotBlank(message = "Symbol is required.")
          @Size(max = 30, message = "Symbol must not exceed 30 characters.")
          String symbol,
      @NotBlank(message = "Accepted input type is required.") String acceptedInputType,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Unit name is required.")
          @Size(max = 150, message = "Unit name must not exceed 150 characters.")
          String name,
      @NotBlank(message = "Symbol is required.")
          @Size(max = 30, message = "Symbol must not exceed 30 characters.")
          String symbol,
      @NotBlank(message = "Accepted input type is required.") String acceptedInputType,
      Boolean active) {}

  public record Response(
      Long id,
      String name,
      String symbol,
      String acceptedInputType,
      Boolean active,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name, String symbol, String acceptedInputType) {}
}
