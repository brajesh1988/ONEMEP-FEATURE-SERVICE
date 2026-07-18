package com.netlink.onemep_feature.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Category master (ONEMEP-22/23/24). */
public final class CategoryDto {
  private CategoryDto() {}

  public record CreateRequest(
      @NotBlank(message = "Category name is required.")
          @Size(max = 150, message = "Category name must not exceed 150 characters.")
          String name,
      @NotBlank(message = "Prefix is required.")
          @Size(max = 10, message = "Prefix must not exceed 10 characters.")
          @Pattern(
              regexp = "^[A-Za-z0-9]+$",
              message = "Prefix may contain only letters and digits.")
          String prefix,
      @Positive(message = "Series code must be a positive number.") Integer seriesCode,
      Boolean active) {}

  /** Name and availability are editable; category number and prefix stay locked. */
  public record UpdateRequest(
      @NotBlank(message = "Category name is required.")
          @Size(max = 150, message = "Category name must not exceed 150 characters.")
          String name,
      Boolean active) {}

  public record Response(
      Long id,
      String categoryNumber,
      String name,
      String prefix,
      Integer seriesCode,
      Boolean active,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name, String prefix, Integer seriesCode) {}
}
