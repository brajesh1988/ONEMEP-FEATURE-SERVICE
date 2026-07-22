package com.netlink.onemep_feature.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for Projects (ONEMEP-12/13/14/15). */
public final class ProjectDto {
  private ProjectDto() {}

  /**
   * Allowed characters for Project Name / Client: letters, numbers, spaces and {@code & . , _ ' -
   * / @ ( ) [ ]}. Location additionally allows {@code " :}.
   */
  public static final String NAME_PATTERN = "^[A-Za-z0-9 &.,_'\\-/@()\\[\\]]+$";

  public static final String LOCATION_PATTERN = "^[A-Za-z0-9 &.,_'\\-/@()\\[\\]\":]+$";

  private static final String NAME_MSG =
      "Only letters, numbers and these special characters are allowed: & . , _ ' - / @ ( ) [ ]";

  /** One project member = a user assigned a team role. */
  public record MemberRequest(
      @NotNull(message = "Member userId is required.") Long userId,
      @NotNull(message = "Member teamRoleId is required.") Long teamRoleId) {}

  /** Category is fixed at creation and cannot be changed later; project number is generated. */
  public record CreateRequest(
      @NotBlank(message = "Project Name is mandatory.")
          @Size(max = 50, message = "Project Name cannot exceed 50 characters.")
          @Pattern(regexp = NAME_PATTERN, message = NAME_MSG)
          String name,
      @NotNull(message = "Category is required.") Long categoryId,
      @NotBlank(message = "Type is required.") String type,
      @NotBlank(message = "Priority is required.") String priority,
      String lifecycleStatus,
      @Size(max = 50, message = "Client cannot exceed 50 characters.")
          @Pattern(regexp = NAME_PATTERN, message = NAME_MSG)
          String client,
      @Size(max = 50, message = "Location cannot exceed 50 characters.")
          @Pattern(regexp = LOCATION_PATTERN, message = NAME_MSG)
          String location,
      Long handlingOfficeId,
      Long detailingLevelId,
      @Size(max = 2000, message = "Description cannot exceed 2000 characters.") String description,
      List<MemberRequest> members) {}

  /** Project ID and category are protected on edit. */
  public record UpdateRequest(
      @NotBlank(message = "Project Name is mandatory.")
          @Size(max = 50, message = "Project Name cannot exceed 50 characters.")
          @Pattern(regexp = NAME_PATTERN, message = NAME_MSG)
          String name,
      @NotBlank(message = "Priority is required.") String priority,
      @NotBlank(message = "Lifecycle is required.") String lifecycleStatus,
      String type,
      @Size(max = 50, message = "Client cannot exceed 50 characters.")
          @Pattern(regexp = NAME_PATTERN, message = NAME_MSG)
          String client,
      @Size(max = 50, message = "Location cannot exceed 50 characters.")
          @Pattern(regexp = LOCATION_PATTERN, message = NAME_MSG)
          String location,
      Long handlingOfficeId,
      Long detailingLevelId,
      @Size(max = 500, message = "Reason cannot exceed 500 characters.") String lifecycleReason,
      @Size(max = 2000, message = "Description cannot exceed 2000 characters.") String description,
      List<MemberRequest> members) {}

  /** A user reference resolved to a display name (lead directory). */
  public record UserRef(Long userId, String userName) {}

  public record MemberResponse(
      Long userId, String userName, Long teamRoleId, String teamRoleName) {}

  public record ListItem(
      Long id,
      String projectNumber,
      String name,
      Long categoryId,
      String categoryName,
      String type,
      String lifecycleStatus,
      String priority,
      Boolean active,
      List<Long> leadUserIds,
      List<UserRef> leadUsers,
      LocalDateTime updatedDate) {}

  public record ActivityItem(
      String action,
      String detail,
      String reason,
      Long performedBy,
      String performedByName,
      LocalDateTime performedAt) {}

  public record Detail(
      Long id,
      String projectNumber,
      String name,
      Long categoryId,
      String categoryName,
      String categoryPrefix,
      Integer categorySeriesCode,
      String type,
      Boolean typeLocked,
      String lifecycleStatus,
      String priority,
      String client,
      String location,
      Long handlingOfficeId,
      String handlingOfficeName,
      Long detailingLevelId,
      String detailingLevelName,
      String description,
      Boolean active,
      List<Long> leadUserIds,
      List<UserRef> leads,
      List<MemberResponse> members,
      Long createdBy,
      String createdByName,
      LocalDateTime createdDate,
      Long updatedBy,
      String updatedByName,
      LocalDateTime updatedDate) {}

  /**
   * Aggregated read-only overview (ONEMEP-15): the detail card, specs sheets, delivery schedule,
   * stakeholder directory, and the recent activity log.
   */
  public record Overview(
      Detail project,
      List<SpecSheetDto.Metadata> specSheets,
      List<DeliveryScheduleDto.Response> deliverySchedule,
      List<StakeholderDto.Response> stakeholders,
      List<ActivityItem> activity) {}
}
