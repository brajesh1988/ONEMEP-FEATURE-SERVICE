package com.netlink.onemep_feature.lookup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the reference-data catalogue. */
public final class LookupDto {
  private LookupDto() {}

  /** Codes appear inside generated Design Numbers, so the character set is deliberately narrow. */
  private static final String CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]*$";

  public record CreateRequest(
      @NotBlank(message = "Code is required.")
          @Size(max = 20, message = "Code must not exceed 20 characters.")
          @Pattern(
              regexp = CODE_PATTERN,
              message = "Code may contain only letters, digits, hyphen and underscore.")
          String code,
      @NotBlank(message = "Label is required.")
          @Size(max = 150, message = "Label must not exceed 150 characters.")
          String label,
      @PositiveOrZero(message = "Sort order must be zero or greater.") Integer sortOrder,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Code is required.")
          @Size(max = 20, message = "Code must not exceed 20 characters.")
          @Pattern(
              regexp = CODE_PATTERN,
              message = "Code may contain only letters, digits, hyphen and underscore.")
          String code,
      @NotBlank(message = "Label is required.")
          @Size(max = 150, message = "Label must not exceed 150 characters.")
          String label,
      @PositiveOrZero(message = "Sort order must be zero or greater.") Integer sortOrder,
      Boolean active) {}

  public record Response(
      Long id,
      String type,
      String code,
      String label,
      Integer sortOrder,
      Boolean active,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  /** Trimmed shape for dropdowns — what every Sprint 3 picker actually needs. */
  public record Option(Long id, String code, String label) {}
}
