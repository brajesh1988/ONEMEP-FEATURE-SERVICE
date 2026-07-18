package com.netlink.onemep_feature.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for Projects (ONEMEP-12/13/14/15). */
public final class ProjectDto {
  private ProjectDto() {}

  /** One project member = a user assigned a team role. */
  public record MemberRequest(
      @NotNull(message = "Member userId is required.") Long userId,
      @NotNull(message = "Member teamRoleId is required.") Long teamRoleId) {}

  /** Category is fixed at creation and cannot be changed later; project number is generated. */
  public record CreateRequest(
      @NotBlank(message = "Project name is required.")
          @Size(max = 200, message = "Project name must not exceed 200 characters.")
          String name,
      @NotNull(message = "Category is required.") Long categoryId,
      String priority,
      @Size(max = 4000, message = "Description must not exceed 4000 characters.")
          String description,
      List<Long> leadUserIds,
      List<MemberRequest> members) {}

  /** Project ID and category are protected on edit. */
  public record UpdateRequest(
      @NotBlank(message = "Project name is required.")
          @Size(max = 200, message = "Project name must not exceed 200 characters.")
          String name,
      String priority,
      String lifecycleStatus,
      @Size(max = 4000, message = "Description must not exceed 4000 characters.")
          String description,
      List<Long> leadUserIds,
      List<MemberRequest> members) {}

  public record MemberResponse(Long userId, Long teamRoleId, String teamRoleName) {}

  public record ListItem(
      Long id,
      String projectNumber,
      String name,
      Long categoryId,
      String categoryName,
      String lifecycleStatus,
      String priority,
      Boolean active,
      List<Long> leadUserIds,
      LocalDateTime updatedDate) {}

  public record Detail(
      Long id,
      String projectNumber,
      String name,
      Long categoryId,
      String categoryName,
      String categoryPrefix,
      String lifecycleStatus,
      String priority,
      String description,
      Boolean active,
      List<Long> leadUserIds,
      List<MemberResponse> members,
      Long createdBy,
      LocalDateTime createdDate,
      Long updatedBy,
      LocalDateTime updatedDate) {}
}
