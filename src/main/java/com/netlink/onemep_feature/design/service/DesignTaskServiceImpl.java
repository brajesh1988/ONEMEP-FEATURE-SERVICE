package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.dto.DesignTaskDto;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.model.DesignTag;
import com.netlink.onemep_feature.design.model.TaskPriority;
import com.netlink.onemep_feature.design.model.TaskReminder;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.design.repo.DesignTagRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignTaskServiceImpl implements DesignTaskService {

  private static final int MAX_TAG_LENGTH = 50;

  private final DesignRepo designRepo;
  private final DesignTagRepo designTagRepo;
  private final ProjectMemberMappingRepo projectMemberMappingRepo;
  private final UserDirectoryClient userDirectoryClient;
  private final DesignActivityService designActivityService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long designId) {
    return apiResponseAdaptor.success(
        "Design task fetched successfully.", toView(require(designId)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long designId, DesignTaskDto.UpdateRequest request) {
    Design design = require(designId);
    List<Runnable> auditEvents = new ArrayList<>();

    applyOwner(design, request, auditEvents);
    applyPriority(design, request, auditEvents);
    applyCompletion(design, request, auditEvents);
    applySchedule(design, request, auditEvents);
    applyReminder(design, request, auditEvents);

    design.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    designRepo.save(design);

    // Emitted only after the change is applied, so a rejected update logs nothing.
    auditEvents.forEach(Runnable::run);
    log.info("Updated task for designId={} events={}", designId, auditEvents.size());

    return apiResponseAdaptor.success("Design task updated successfully.", toView(design));
  }

  @Override
  @Transactional
  public ApiResponse<?> addTag(Long designId, DesignTaskDto.AddTagRequest request) {
    Design design = require(designId);

    String label = request.label() == null ? "" : request.label().trim();
    if (label.isEmpty()) {
      throw new ApplicationException("Enter a valid Tag.");
    }
    if (label.length() > MAX_TAG_LENGTH) {
      throw new ApplicationException("Tag must not exceed " + MAX_TAG_LENGTH + " characters.");
    }

    String normalized = label.toLowerCase();
    designTagRepo
        .findByDesignAndNormalizedLabel(designId, normalized)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException("This Tag is already added to the Design.");
            });

    DesignTag tag = new DesignTag();
    tag.setDesign(design);
    tag.setLabel(label);
    tag.setLabelNormalized(normalized);
    tag.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    designTagRepo.save(tag);

    designActivityService.record(
        design, ActivityAction.TAG_ADDED, "Tag " + quoted(label) + " added");

    return apiResponseAdaptor.success("Tag added successfully.", toView(design));
  }

  @Override
  @Transactional
  public ApiResponse<?> removeTag(Long designId, Long tagId) {
    Design design = require(designId);
    DesignTag tag =
        designTagRepo
            .findByIdAndDesign(tagId, designId)
            .orElseThrow(() -> new ResourceNotFoundException("This Tag is no longer available."));

    String label = tag.getLabel();
    designTagRepo.delete(tag);
    designActivityService.record(
        design, ActivityAction.TAG_REMOVED, "Tag " + quoted(label) + " removed");

    return apiResponseAdaptor.success("Tag removed successfully.", toView(design));
  }

  // ── field application ─────────────────────────────────────────────────────

  private void applyOwner(
      Design design, DesignTaskDto.UpdateRequest request, List<Runnable> auditEvents) {
    Long previous = design.getOwnerId();

    if (Boolean.TRUE.equals(request.clearOwner())) {
      if (previous == null) {
        return;
      }
      design.setOwnerId(null);
      String before = displayName(previous);
      auditEvents.add(
          () ->
              designActivityService.record(
                  design, ActivityAction.OWNER_CHANGED, "Owner cleared (was " + before + ")"));
      return;
    }

    Long next = request.ownerId();
    if (next == null || Objects.equals(previous, next)) {
      return;
    }

    requireEligibleOwner(design, next);
    design.setOwnerId(next);

    String before = previous == null ? "(unassigned)" : displayName(previous);
    String after = displayName(next);
    auditEvents.add(
        () ->
            designActivityService.record(
                design,
                ActivityAction.OWNER_CHANGED,
                "Owner changed from " + before + " to " + after));
  }

  /**
   * ONEMEP-38: "Owner must be an eligible user for the applicable Project." Eligibility is project
   * membership, so a user from another project cannot be assigned even though they exist.
   */
  private void requireEligibleOwner(Design design, Long userId) {
    Set<Long> eligible =
        projectMemberMappingRepo.findByProject_Id(design.getProject().getId()).stream()
            .map(ProjectMemberMapping::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (!eligible.contains(userId)) {
      throw new ApplicationException(
          "The selected Owner is no longer available. Select another Project user.");
    }
  }

  private void applyPriority(
      Design design, DesignTaskDto.UpdateRequest request, List<Runnable> auditEvents) {
    TaskPriority next = request.priority();
    if (next == null || next == design.getPriority()) {
      return;
    }
    TaskPriority before = design.getPriority();
    design.setPriority(next);
    auditEvents.add(
        () ->
            designActivityService.record(
                design,
                ActivityAction.PRIORITY_CHANGED,
                "Priority changed from " + label(before) + " to " + label(next)));
  }

  private void applyCompletion(
      Design design, DesignTaskDto.UpdateRequest request, List<Runnable> auditEvents) {
    Integer next = request.completionPct();
    if (next == null || Objects.equals(next, design.getCompletionPct())) {
      return;
    }
    if (next < 0 || next > 100) {
      throw new ApplicationException("Completion must be between 0 and 100%.");
    }
    Integer before = design.getCompletionPct();
    design.setCompletionPct(next);
    auditEvents.add(
        () ->
            designActivityService.record(
                design,
                ActivityAction.COMPLETION_CHANGED,
                "Completion updated from " + before + "% to " + next + "%"));
  }

  /**
   * Start and Due are validated together against their <em>resulting</em> pair, not the incoming
   * one — changing only the Start Date must still be checked against the stored Due Date.
   */
  private void applySchedule(
      Design design, DesignTaskDto.UpdateRequest request, List<Runnable> auditEvents) {
    LocalDate previousStart = design.getStartDate();
    LocalDate previousDue = design.getDueDate();

    LocalDate nextStart =
        Boolean.TRUE.equals(request.clearStartDate())
            ? null
            : (request.startDate() == null ? previousStart : request.startDate());
    LocalDate nextDue =
        Boolean.TRUE.equals(request.clearDueDate())
            ? null
            : (request.dueDate() == null ? previousDue : request.dueDate());

    if (nextStart != null && nextDue != null && nextDue.isBefore(nextStart)) {
      throw new ApplicationException("Due Date cannot be earlier than Start Date.");
    }

    if (!Objects.equals(previousStart, nextStart)) {
      design.setStartDate(nextStart);
      auditEvents.add(
          () ->
              designActivityService.record(
                  design,
                  ActivityAction.SCHEDULE_CHANGED,
                  "Start Date changed from " + date(previousStart) + " to " + date(nextStart)));
    }
    if (!Objects.equals(previousDue, nextDue)) {
      design.setDueDate(nextDue);
      auditEvents.add(
          () ->
              designActivityService.record(
                  design,
                  ActivityAction.SCHEDULE_CHANGED,
                  "Due Date changed from " + date(previousDue) + " to " + date(nextDue)));
    }
  }

  private void applyReminder(
      Design design, DesignTaskDto.UpdateRequest request, List<Runnable> auditEvents) {
    TaskReminder next = request.reminder();
    if (next == null || next == design.getReminder()) {
      return;
    }
    TaskReminder before = design.getReminder();
    design.setReminder(next);
    auditEvents.add(
        () ->
            designActivityService.record(
                design,
                ActivityAction.REMINDER_CHANGED,
                "Reminder changed from " + label(before) + " to " + label(next)));
  }

  // ── mapping ───────────────────────────────────────────────────────────────

  @Override
  public DesignTaskDto.View toView(Design design) {
    List<DesignTaskDto.TagView> tags =
        designTagRepo.findForDesign(design.getId()).stream()
            .map(t -> new DesignTaskDto.TagView(t.getId(), t.getLabel()))
            .toList();

    DesignTaskDto.OwnerView owner =
        design.getOwnerId() == null
            ? null
            : new DesignTaskDto.OwnerView(design.getOwnerId(), displayName(design.getOwnerId()));

    return new DesignTaskDto.View(
        owner,
        design.getStatus(),
        design.getPriority(),
        design.getCompletionPct(),
        tags,
        design.getStartDate(),
        design.getDueDate(),
        design.durationDays().orElse(null),
        design.getReminder(),
        design.getReminder().dueOn(design.getDueDate()).orElse(null),
        design.getSource());
  }

  private Design require(Long designId) {
    return designRepo
        .findById(designId)
        .orElseThrow(() -> new ResourceNotFoundException("This Design is no longer available."));
  }

  /** Falls back to a stable placeholder — the directory being down must not fail the write. */
  private String displayName(Long userId) {
    if (userId == null) {
      return "(unassigned)";
    }
    UserSummary summary = userDirectoryClient.resolve(List.of(userId)).get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  /**
   * Enum constants read as prose in the audit trail: {@code ONE_WEEK_BEFORE} → "One week before".
   */
  private static String label(Enum<?> value) {
    String words = value.name().toLowerCase().replace('_', ' ');
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  private static String date(LocalDate value) {
    return value == null ? "(none)" : value.toString();
  }

  private static String quoted(String value) {
    return "'" + value + "'";
  }
}
