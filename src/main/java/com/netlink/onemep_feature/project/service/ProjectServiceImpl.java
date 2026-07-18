package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.category.repo.CategoryRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.detailinglevel.model.DetailingLevelMaster;
import com.netlink.onemep_feature.detailinglevel.repo.DetailingLevelRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.handlingoffice.model.HandlingOfficeMaster;
import com.netlink.onemep_feature.handlingoffice.repo.HandlingOfficeRepo;
import com.netlink.onemep_feature.notification.ProjectNotificationService;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;
import com.netlink.onemep_feature.project.dto.ProjectDto;
import com.netlink.onemep_feature.project.dto.SpecSheetDto;
import com.netlink.onemep_feature.project.dto.StakeholderDto;
import com.netlink.onemep_feature.project.model.ProjectActivityLog;
import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import com.netlink.onemep_feature.project.model.ProjectLeadMapping;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.model.ProjectStakeholder;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectLeadMappingRepo;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectSpecSheetRepo;
import com.netlink.onemep_feature.project.repo.ProjectStakeholderRepo;
import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import com.netlink.onemep_feature.teamrole.repo.TeamRoleRepo;
import com.netlink.onemep_feature.user.model.UserAccountRef;
import com.netlink.onemep_feature.user.repo.UserAccountRefRepo;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

  private static final Set<String> SORTABLE =
      Set.of(
          "name",
          "projectNumber",
          "lifecycleStatus",
          "priority",
          "type",
          "active",
          "createdDate",
          "updatedDate");
  private static final Set<String> LIFECYCLE =
      Set.of("DRAFT", "ACTIVE", "ON_HOLD", "COMPLETED", "CLOSED", "CANCELLED", "ARCHIVED");
  private static final Set<String> PRIORITY = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
  private static final Set<String> TYPES = Set.of("CONFIRMED", "NON_CONFIRMED");

  /** Lifecycle transitions that require the user to supply a reason (ONEMEP-12/14/15). */
  private static final Set<String> REASON_REQUIRED = Set.of("ON_HOLD", "CLOSED");

  private final ProjectRepo projectRepo;
  private final ProjectLeadMappingRepo leadRepo;
  private final ProjectMemberMappingRepo memberRepo;
  private final ProjectActivityLogRepo activityRepo;
  private final ProjectSpecSheetRepo specSheetRepo;
  private final ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  private final ProjectStakeholderRepo stakeholderRepo;
  private final CategoryRepo categoryRepo;
  private final TeamRoleRepo teamRoleRepo;
  private final HandlingOfficeRepo handlingOfficeRepo;
  private final DetailingLevelRepo detailingLevelRepo;
  private final UserAccountRefRepo userRepo;
  private final ProjectNotificationService notificationService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    Page<ProjectMaster> page =
        projectRepo.findAll(buildSpec(request), PageableFactory.of(request, SORTABLE));
    List<ProjectDto.ListItem> content = page.getContent().stream().map(this::toListItem).toList();
    return apiResponseAdaptor.success(
        "Projects fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> create(ProjectDto.CreateRequest request) {
    String name = normalize(request.name());
    projectRepo
        .findByNameIgnoreCase(name)
        .ifPresent(
            p -> {
              throw new DuplicateResourceException("A project with this name already exists.");
            });
    CategoryMaster category = requireCategory(request.categoryId());
    String type = validateType(request.type());
    String priority = validatePriority(request.priority());
    String lifecycle =
        request.lifecycleStatus() == null || request.lifecycleStatus().isBlank()
            ? "ACTIVE"
            : validateLifecycle(request.lifecycleStatus());
    // Fail fast: a confirmed project needs a numeric category series to build its Project ID.
    if ("CONFIRMED".equals(type)) {
      requireSeriesCode(category);
    }

    ProjectMaster project = new ProjectMaster();
    project.setName(name);
    project.setCategory(category);
    project.setType(type);
    project.setTypeLocked("CONFIRMED".equals(type));
    project.setPriority(priority);
    project.setLifecycleStatus(lifecycle);
    project.setClient(trimToNull(request.client()));
    project.setLocation(trimToNull(request.location()));
    project.setHandlingOffice(resolveHandlingOffice(request.handlingOfficeId()));
    project.setDetailingLevel(resolveDetailingLevel(request.detailingLevelId()));
    project.setDescription(trimToNull(request.description()));
    project.setActive(Boolean.TRUE);
    project.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    project.setProjectNumber("TMP-" + UUID.randomUUID());
    project = projectRepo.saveAndFlush(project);
    project.setProjectNumber(generateProjectNumber(type, category, project.getId()));
    project = projectRepo.save(project);

    replaceLeads(project, request.leadUserIds());
    replaceMembers(project, request.members());
    logActivity(project, "PROJECT_CREATED", "Project created as " + type, null);

    log.info("Created projectId={} number={}", project.getId(), project.getProjectNumber());
    return apiResponseAdaptor.success("Project created successfully.", toDetail(project));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Project fetched successfully.", toDetail(require(id)));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> overview(Long id) {
    // Project Overview (ONEMEP-15): detail card + specs sheets + delivery schedule +
    // stakeholder directory + recent activity log.
    ProjectMaster project = require(id);
    List<SpecSheetDto.Metadata> specSheets = specSheetRepo.findMetadataByProjectId(id);
    List<DeliveryScheduleDto.Response> deliverySchedule =
        deliveryScheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(id).stream()
            .map(this::toDeliveryResponse)
            .toList();
    List<StakeholderDto.Response> stakeholders =
        stakeholderRepo.findByProject_IdOrderByRoleAscNameAsc(id).stream()
            .map(this::toStakeholderResponse)
            .toList();
    List<ProjectDto.ActivityItem> activity =
        activityRepo.findByProject_IdOrderByCreatedDateDescIdDesc(id).stream()
            .map(
                a ->
                    new ProjectDto.ActivityItem(
                        a.getAction(),
                        a.getDetail(),
                        a.getReason(),
                        a.getCreatedBy(),
                        a.getCreatedDate()))
            .toList();
    return apiResponseAdaptor.success(
        "Project overview fetched successfully.",
        new ProjectDto.Overview(
            toDetail(project), specSheets, deliverySchedule, stakeholders, activity));
  }

  private DeliveryScheduleDto.Response toDeliveryResponse(ProjectDeliverySchedule d) {
    return new DeliveryScheduleDto.Response(
        d.getId(),
        d.getMilestone(),
        d.getDeliverable(),
        d.getPlannedDate(),
        d.getActualDate(),
        d.getStatus(),
        d.getRemarks(),
        d.getUpdatedBy(),
        d.getUpdatedDate());
  }

  private StakeholderDto.Response toStakeholderResponse(ProjectStakeholder s) {
    return new StakeholderDto.Response(
        s.getId(),
        s.getRole(),
        s.getName(),
        s.getOrganization(),
        s.getEmail(),
        s.getPhone(),
        s.getUpdatedBy(),
        s.getUpdatedDate());
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, ProjectDto.UpdateRequest request) {
    ProjectMaster project = require(id);
    String name = normalize(request.name());
    projectRepo
        .findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            p -> {
              throw new DuplicateResourceException("A project with this name already exists.");
            });
    // project_number and category are intentionally NOT updated — they are protected.
    String oldLifecycle = project.getLifecycleStatus();
    String oldPriority = project.getPriority();

    project.setName(name);
    project.setPriority(validatePriority(request.priority()));

    String newLifecycle = validateLifecycle(request.lifecycleStatus());
    String reason = trimToNull(request.lifecycleReason());
    if (!Objects.equals(oldLifecycle, newLifecycle)) {
      requireReasonIfNeeded(newLifecycle, reason);
    }
    project.setLifecycleStatus(newLifecycle);

    project.setClient(trimToNull(request.client()));
    project.setLocation(trimToNull(request.location()));
    project.setHandlingOffice(resolveHandlingOffice(request.handlingOfficeId()));
    project.setDetailingLevel(resolveDetailingLevel(request.detailingLevelId()));
    project.setDescription(trimToNull(request.description()));

    boolean confirmed = applyTypeChange(project, request.type());

    project.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    projectRepo.save(project);

    if (request.leadUserIds() != null) {
      replaceLeads(project, request.leadUserIds());
    }
    if (request.members() != null) {
      replaceMembers(project, request.members());
    }

    Long actor = SecurityUtils.getUserId().orElse(null);
    List<Long> leadIds = currentLeadUserIds(project.getId());
    logActivity(project, "PROJECT_UPDATED", "Project details updated", null);
    if (!Objects.equals(oldLifecycle, project.getLifecycleStatus())) {
      logActivity(
          project,
          "LIFECYCLE_CHANGED",
          oldLifecycle + " → " + project.getLifecycleStatus(),
          reason);
      notificationService.notifyLifecycleChanged(
          project, oldLifecycle, project.getLifecycleStatus(), reason, actor, leadIds);
    }
    if (!Objects.equals(oldPriority, project.getPriority())) {
      logActivity(project, "PRIORITY_CHANGED", oldPriority + " → " + project.getPriority(), null);
      notificationService.notifyPriorityChanged(
          project, oldPriority, project.getPriority(), actor, leadIds);
    }
    if (confirmed) {
      logActivity(
          project,
          "TYPE_CONFIRMED",
          "Project confirmed; number " + project.getProjectNumber(),
          null);
    }
    log.info("Updated projectId={}", id);
    return apiResponseAdaptor.success("Project updated successfully.", toDetail(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    ProjectMaster project = require(id);
    project.setActive(active);
    project.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    projectRepo.save(project);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Project activated successfully."
            : "Project deactivated successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> updateLifecycle(Long id, String lifecycleStatus, String reason) {
    ProjectMaster project = require(id);
    String oldStatus = project.getLifecycleStatus();
    String newStatus = validateLifecycle(lifecycleStatus);
    String cleanReason = trimToNull(reason);
    if (!Objects.equals(oldStatus, newStatus)) {
      requireReasonIfNeeded(newStatus, cleanReason);
    }
    project.setLifecycleStatus(newStatus);
    project.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    projectRepo.save(project);
    if (!Objects.equals(oldStatus, newStatus)) {
      logActivity(project, "LIFECYCLE_CHANGED", oldStatus + " → " + newStatus, cleanReason);
      notificationService.notifyLifecycleChanged(
          project,
          oldStatus,
          newStatus,
          cleanReason,
          SecurityUtils.getUserId().orElse(null),
          currentLeadUserIds(id));
    }
    return apiResponseAdaptor.success("Project lifecycle updated successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> updatePriority(Long id, String priority) {
    ProjectMaster project = require(id);
    String oldPriority = project.getPriority();
    String newPriority = validatePriority(priority);
    project.setPriority(newPriority);
    project.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    projectRepo.save(project);
    if (!Objects.equals(oldPriority, newPriority)) {
      logActivity(project, "PRIORITY_CHANGED", oldPriority + " → " + newPriority, null);
      notificationService.notifyPriorityChanged(
          project,
          oldPriority,
          newPriority,
          SecurityUtils.getUserId().orElse(null),
          currentLeadUserIds(id));
    }
    return apiResponseAdaptor.success("Project priority updated successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> updateType(Long id, String type) {
    ProjectMaster project = require(id);
    boolean confirmed = applyTypeChange(project, type);
    if (!confirmed) {
      // No-op change (already the requested type) — nothing to persist.
      return apiResponseAdaptor.success("Project type unchanged.", toDetail(project));
    }
    project.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    projectRepo.save(project);
    logActivity(
        project, "TYPE_CONFIRMED", "Project confirmed; number " + project.getProjectNumber(), null);
    log.info("Confirmed projectId={} newNumber={}", id, project.getProjectNumber());
    return apiResponseAdaptor.success("Project confirmed successfully.", toDetail(project));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /**
   * Applies a requested Type transition. Only NON_CONFIRMED → CONFIRMED is allowed; it reassigns
   * the Project ID from the category series and locks the type. Returns {@code true} when the
   * project was just confirmed, {@code false} when the request is a no-op (same type).
   */
  private boolean applyTypeChange(ProjectMaster project, String requestedType) {
    if (requestedType == null || requestedType.isBlank()) {
      return false;
    }
    String target = validateType(requestedType);
    if (target.equals(project.getType())) {
      return false;
    }
    if ("NON_CONFIRMED".equals(target)) {
      throw new ApplicationException(
          "A confirmed project cannot be changed back to Non-confirmed.");
    }
    // target == CONFIRMED, current == NON_CONFIRMED
    if (Boolean.TRUE.equals(project.getTypeLocked())) {
      throw new ApplicationException("Project type is locked and cannot be changed.");
    }
    CategoryMaster category = project.getCategory();
    requireSeriesCode(category);
    project.setType("CONFIRMED");
    project.setTypeLocked(Boolean.TRUE);
    project.setProjectNumber(generateProjectNumber("CONFIRMED", category, project.getId()));
    return true;
  }

  private void replaceLeads(ProjectMaster project, List<Long> leadUserIds) {
    if (leadUserIds == null) {
      leadRepo.deleteByProject_Id(project.getId());
      return;
    }
    Set<Long> distinct = new LinkedHashSet<>();
    for (Long userId : leadUserIds) {
      if (userId != null) {
        distinct.add(userId);
      }
    }
    requireUsersExist(distinct);
    leadRepo.deleteByProject_Id(project.getId());
    // Flush the deletes before inserting replacements: otherwise Hibernate orders the new
    // INSERTs ahead of the DELETEs in a single flush and trips the uq_project_lead constraint.
    leadRepo.flush();
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    for (Long userId : distinct) {
      ProjectLeadMapping lead = new ProjectLeadMapping();
      lead.setProject(project);
      lead.setUserId(userId);
      lead.setCreatedBy(currentUser);
      leadRepo.save(lead);
    }
  }

  private void replaceMembers(ProjectMaster project, List<ProjectDto.MemberRequest> members) {
    if (members == null) {
      memberRepo.deleteByProject_Id(project.getId());
      return;
    }
    Set<Long> userIds = new LinkedHashSet<>();
    for (ProjectDto.MemberRequest m : members) {
      if (m != null && m.userId() != null) {
        userIds.add(m.userId());
      }
    }
    requireUsersExist(userIds);
    memberRepo.deleteByProject_Id(project.getId());
    // Flush deletes before re-inserting to avoid the INSERT-before-DELETE unique-constraint clash.
    memberRepo.flush();
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    Set<String> seen = new LinkedHashSet<>();
    for (ProjectDto.MemberRequest m : members) {
      if (m == null || m.userId() == null || m.teamRoleId() == null) {
        continue;
      }
      if (!seen.add(m.userId() + ":" + m.teamRoleId())) {
        continue;
      }
      TeamRoleMaster role =
          teamRoleRepo
              .findById(m.teamRoleId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Team role not found for a project member."));
      ProjectMemberMapping member = new ProjectMemberMapping();
      member.setProject(project);
      member.setUserId(m.userId());
      member.setTeamRole(role);
      member.setCreatedBy(currentUser);
      memberRepo.save(member);
    }
  }

  /** Fails with a friendly 404 (instead of a raw FK violation) when a referenced user is absent. */
  private void requireUsersExist(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    Set<Long> found =
        userRepo.findByIdIn(userIds).stream()
            .map(UserAccountRef::getId)
            .collect(Collectors.toSet());
    Set<Long> missing = new TreeSet<>(userIds);
    missing.removeAll(found);
    if (!missing.isEmpty()) {
      throw new ResourceNotFoundException("User(s) not found: " + missing);
    }
  }

  private List<Long> currentLeadUserIds(Long projectId) {
    return leadRepo.findByProject_Id(projectId).stream()
        .map(ProjectLeadMapping::getUserId)
        .filter(Objects::nonNull)
        .toList();
  }

  private void logActivity(ProjectMaster project, String action, String detail, String reason) {
    ProjectActivityLog entry = new ProjectActivityLog();
    entry.setProject(project);
    entry.setAction(action);
    entry.setDetail(detail);
    entry.setReason(reason);
    entry.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    activityRepo.save(entry);
  }

  private ProjectMaster require(Long id) {
    return projectRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private CategoryMaster requireCategory(Long categoryId) {
    return categoryRepo
        .findById(categoryId)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
  }

  private HandlingOfficeMaster resolveHandlingOffice(Long id) {
    if (id == null) {
      return null;
    }
    return handlingOfficeRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Handling office not found."));
  }

  private DetailingLevelMaster resolveDetailingLevel(Long id) {
    if (id == null) {
      return null;
    }
    return detailingLevelRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Detailing level not found."));
  }

  private static void requireSeriesCode(CategoryMaster category) {
    if (category.getSeriesCode() == null) {
      throw new ApplicationException(
          "Category \""
              + category.getName()
              + "\" has no series code configured; a confirmed Project ID cannot be generated.");
    }
  }

  /** Confirmed → {series}{id:0000}; Non-confirmed → NC{id:0000}. */
  private static String generateProjectNumber(String type, CategoryMaster category, Long id) {
    if ("CONFIRMED".equals(type)) {
      return category.getSeriesCode() + String.format("%04d", id);
    }
    return "NC" + String.format("%04d", id);
  }

  private static void requireReasonIfNeeded(String lifecycle, String reason) {
    if (REASON_REQUIRED.contains(lifecycle) && (reason == null || reason.isBlank())) {
      throw new ApplicationException(
          "A reason is required when changing lifecycle to On Hold or Closed.");
    }
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String validateType(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    if (!TYPES.contains(value)) {
      throw new ApplicationException("Type must be one of: CONFIRMED, NON_CONFIRMED.");
    }
    return value;
  }

  private static String validatePriority(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApplicationException("Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL.");
    }
    String value = raw.trim().toUpperCase();
    if (!PRIORITY.contains(value)) {
      throw new ApplicationException("Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL.");
    }
    return value;
  }

  private static String validateLifecycle(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase();
    if (!LIFECYCLE.contains(value)) {
      throw new ApplicationException(
          "Lifecycle status must be one of: DRAFT, ACTIVE, ON_HOLD, COMPLETED, CLOSED, CANCELLED,"
              + " ARCHIVED.");
    }
    return value;
  }

  /**
   * Builds the list specification: a free-text {@code search} across name + project number +
   * client, plus structured filters ({@code lifecycleStatus}, {@code priority}, {@code type},
   * {@code categoryId}, {@code active}). Invalid enum values yield a 400.
   */
  private Specification<ProjectMaster> buildSpec(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Map<String, Object> filters = request == null ? null : request.getFilters();
    String lifecycle = readEnumFilter(filters, "lifecycleStatus", LIFECYCLE, "Lifecycle status");
    String priority = readEnumFilter(filters, "priority", PRIORITY, "Priority");
    String type = readEnumFilter(filters, "type", TYPES, "Type");
    Long categoryId = readLongFilter(filters, "categoryId");
    Boolean active = readBooleanFilter(filters, "active");

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (search != null) {
        String like = "%" + search.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("projectNumber")), like),
                cb.like(cb.lower(cb.coalesce(root.get("client"), "")), like)));
      }
      if (lifecycle != null) {
        predicates.add(cb.equal(root.get("lifecycleStatus"), lifecycle));
      }
      if (priority != null) {
        predicates.add(cb.equal(root.get("priority"), priority));
      }
      if (type != null) {
        predicates.add(cb.equal(root.get("type"), type));
      }
      if (categoryId != null) {
        predicates.add(cb.equal(root.get("category").get("id"), categoryId));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static String readEnumFilter(
      Map<String, Object> filters, String key, Set<String> allowed, String label) {
    if (filters == null || filters.get(key) == null) {
      return null;
    }
    String value = filters.get(key).toString().trim().toUpperCase().replace('-', '_');
    if (value.isEmpty()) {
      return null;
    }
    if (!allowed.contains(value)) {
      throw new ApplicationException(label + " filter must be one of: " + new TreeSet<>(allowed));
    }
    return value;
  }

  private static Long readLongFilter(Map<String, Object> filters, String key) {
    if (filters == null || filters.get(key) == null) {
      return null;
    }
    Object raw = filters.get(key);
    try {
      if (raw instanceof Number number) {
        return number.longValue();
      }
      String value = raw.toString().trim();
      return value.isEmpty() ? null : Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw new ApplicationException("Filter '" + key + "' must be a numeric id.");
    }
  }

  private static Boolean readBooleanFilter(Map<String, Object> filters, String key) {
    if (filters == null || filters.get(key) == null) {
      return null;
    }
    Object raw = filters.get(key);
    if (raw instanceof Boolean bool) {
      return bool;
    }
    String value = raw.toString().trim();
    if (value.isEmpty()) {
      return null;
    }
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return Boolean.parseBoolean(value);
    }
    throw new ApplicationException("Filter '" + key + "' must be true or false.");
  }

  private ProjectDto.ListItem toListItem(ProjectMaster p) {
    List<Long> leadIds =
        leadRepo.findByProject_Id(p.getId()).stream()
            .map(ProjectLeadMapping::getUserId)
            .filter(Objects::nonNull)
            .toList();
    return new ProjectDto.ListItem(
        p.getId(),
        p.getProjectNumber(),
        p.getName(),
        p.getCategory().getId(),
        p.getCategory().getName(),
        p.getType(),
        p.getLifecycleStatus(),
        p.getPriority(),
        p.getActive(),
        leadIds,
        p.getUpdatedDate());
  }

  private ProjectDto.Detail toDetail(ProjectMaster p) {
    List<Long> leadIds =
        leadRepo.findByProject_Id(p.getId()).stream()
            .map(ProjectLeadMapping::getUserId)
            .filter(Objects::nonNull)
            .toList();
    List<ProjectDto.MemberResponse> members =
        memberRepo.findByProject_Id(p.getId()).stream()
            .map(
                m ->
                    new ProjectDto.MemberResponse(
                        m.getUserId(), m.getTeamRole().getId(), m.getTeamRole().getName()))
            .toList();
    CategoryMaster category = p.getCategory();
    HandlingOfficeMaster office = p.getHandlingOffice();
    DetailingLevelMaster level = p.getDetailingLevel();
    return new ProjectDto.Detail(
        p.getId(),
        p.getProjectNumber(),
        p.getName(),
        category.getId(),
        category.getName(),
        category.getPrefix(),
        category.getSeriesCode(),
        p.getType(),
        p.getTypeLocked(),
        p.getLifecycleStatus(),
        p.getPriority(),
        p.getClient(),
        p.getLocation(),
        office == null ? null : office.getId(),
        office == null ? null : office.getName(),
        level == null ? null : level.getId(),
        level == null ? null : level.getName(),
        p.getDescription(),
        p.getActive(),
        leadIds,
        members,
        p.getCreatedBy(),
        p.getCreatedDate(),
        p.getUpdatedBy(),
        p.getUpdatedDate());
  }
}
