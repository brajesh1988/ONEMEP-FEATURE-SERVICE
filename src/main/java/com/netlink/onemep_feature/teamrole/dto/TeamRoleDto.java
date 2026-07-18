package com.netlink.onemep_feature.teamrole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Request/response payloads for the Team Role master (ONEMEP-19/20/21). */
public final class TeamRoleDto {
  private TeamRoleDto() {}

  public record CreateRequest(
      @NotBlank(message = "Team role name is required.")
          @Size(max = 150, message = "Team role name must not exceed 150 characters.")
          String name,
      @NotNull(message = "Tier is required.") Long tierId,
      Boolean active) {}

  public record UpdateRequest(
      @NotBlank(message = "Team role name is required.")
          @Size(max = 150, message = "Team role name must not exceed 150 characters.")
          String name,
      @NotNull(message = "Tier is required.") Long tierId,
      Boolean active) {}

  public record Response(
      Long id,
      String name,
      Long tierId,
      String tierName,
      Boolean active,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  public record ActiveItem(Long id, String name, Long tierId, String tierName) {}
}
