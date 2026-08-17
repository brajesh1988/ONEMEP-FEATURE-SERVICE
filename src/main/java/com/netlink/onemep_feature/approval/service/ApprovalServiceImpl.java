package com.netlink.onemep_feature.approval.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.approval.dto.ApprovalDto;
import com.netlink.onemep_feature.approval.model.ApprovalAction;
import com.netlink.onemep_feature.approval.model.ApprovalAssignee;
import com.netlink.onemep_feature.approval.model.ApprovalChecklistSnapshot;
import com.netlink.onemep_feature.approval.model.ApprovalNote;
import com.netlink.onemep_feature.approval.model.ApprovalRequest;
import com.netlink.onemep_feature.approval.model.ApprovalStage;
import com.netlink.onemep_feature.approval.model.ApprovalStatus;
import com.netlink.onemep_feature.approval.model.ApproverDecision;
import com.netlink.onemep_feature.approval.repo.ApprovalChecklistSnapshotRepo;
import com.netlink.onemep_feature.approval.repo.ApprovalNoteRepo;
import com.netlink.onemep_feature.approval.repo.ApprovalRequestRepo;
import com.netlink.onemep_feature.checklist.model.ChecklistItem;
import com.netlink.onemep_feature.checklist.model.ChecklistMaster;
import com.netlink.onemep_feature.checklist.model.ChecklistRecordType;
import com.netlink.onemep_feature.checklist.repo.ChecklistMasterRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.DateUtils;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.model.DesignStatus;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.file.model.DesignFile;
import com.netlink.onemep_feature.file.model.DesignFileVersion;
import com.netlink.onemep_feature.file.repo.DesignFileCommentRepo;
import com.netlink.onemep_feature.file.repo.DesignFileRepo;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approval state machine (ONEMEP-40).
 *
 * <p>Every state change funnels through {@link #decide}, {@link #recall}, {@link #reassign} or
 * {@link #cancel}; nothing else mutates a request. The invariants that matter:
 *
 * <ul>
 *   <li>a request is raised against the file's <em>current</em> revision, and that binding is
 *       frozen — later uploads never move it;
 *   <li>a revision gets one completed cycle, ever;
 *   <li>all live approvers at a stage must approve, but a single edit-request or rejection closes
 *       the whole request;
 *   <li>the checklist is copied, not referenced, so editing the master cannot rewrite history.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

  private final ApprovalRequestRepo approvalRequestRepo;
  private final ApprovalNoteRepo approvalNoteRepo;
  private final ApprovalChecklistSnapshotRepo approvalChecklistSnapshotRepo;
  private final DesignFileRepo designFileRepo;
  private final DesignFileCommentRepo designFileCommentRepo;
  private final DesignRepo designRepo;
  private final ChecklistMasterRepo checklistMasterRepo;
  private final ProjectMemberMappingRepo projectMemberMappingRepo;
  private final PrincipalResolver principalResolver;
  private final UserDirectoryClient userDirectoryClient;
  private final DesignActivityService designActivityService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  // ── context ───────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> submissionContext(Long fileId) {
    DesignFile file = requireFile(fileId);
    DesignFileVersion current = requireCurrentVersion(file);
    Design design = file.getDesign();
    Long requesterId = currentUserId();

    List<ChecklistMaster> applicable = applicableChecklists(design);
    List<ApprovalDto.ApplicableItem> items = new ArrayList<>();
    applicable.forEach(
        checklist ->
            checklist
                .getItems()
                .forEach(
                    item ->
                        items.add(
                            new ApprovalDto.ApplicableItem(
                                item.getId(), nameOf(checklist), item.getText()))));

    List<Long> principals = principalResolver.principalsOf(design.getProject().getId());
    List<Long> eligible =
        eligibleApproverIds(design).stream().filter(id -> !id.equals(requesterId)).toList();
    Map<Long, UserSummary> users = resolveUsers(eligible);

    // Routing is only meaningful when a Principal exists who is not the requester — otherwise the
    // workflow would either have no second stage or route straight back to the person who raised
    // it.
    boolean routingAvailable =
        !principals.isEmpty() && principals.stream().anyMatch(id -> !id.equals(requesterId));

    return apiResponseAdaptor.success(
        items.isEmpty()
            ? "No checklist is configured for this Discipline, Type and Subject combination."
            : "Approval context fetched successfully.",
        new ApprovalDto.SubmissionContext(
            file.getId(),
            file.getDisplayName(),
            current.getId(),
            current.getRevisionLabel(),
            current.getFileExtension(),
            labelOf(design.getDiscipline().getCode(), design.getDiscipline().getLabel()),
            labelOf(design.getType().getCode(), design.getType().getLabel()),
            labelOf(design.getSubject().getCode(), design.getSubject().getLabel()),
            items,
            eligible.stream()
                .map(
                    id ->
                        new ApprovalDto.EligibleApprover(
                            id, nameOf(users, id), principals.contains(id)))
                .toList(),
            routingAvailable,
            designFileCommentRepo.countOpenForFile(fileId) > 0));
  }

  // ── submit ────────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> submit(Long fileId, ApprovalDto.SubmitRequest request) {
    DesignFile file = requireFile(fileId);
    DesignFileVersion current = requireCurrentVersion(file);
    Design design = file.getDesign();
    Long requesterId = requireCurrentUserId();

    if (approvalRequestRepo.hasPendingForFile(fileId)) {
      throw new ResourceInUseException(
          "An Approval Request is already pending for this file. Complete or recall the existing"
              + " request before sending it again.");
    }
    if (approvalRequestRepo.hasCompletedCycle(current.getId())) {
      throw new ResourceInUseException(
          current.getRevisionLabel()
              + " has already completed an approval cycle. Upload a new version of this file before"
              + " sending it for approval again.");
    }

    List<Long> approverIds = distinct(request.approverIds());
    if (approverIds.isEmpty()) {
      throw new ApplicationException("Select at least one Approver.");
    }
    if (approverIds.contains(requesterId)) {
      throw new ApplicationException("You cannot assign an Approval Request to yourself.");
    }
    Set<Long> eligible = eligibleApproverIds(design);
    if (!eligible.containsAll(approverIds)) {
      throw new ApplicationException(
          "One or more selected Approvers are no longer available. Update the Approver selection"
              + " and try again.");
    }

    List<ChecklistMaster> applicable = applicableChecklists(design);
    requireChecklistComplete(applicable, request.checkedItemIds());

    boolean routeToPrincipal = Boolean.TRUE.equals(request.routeToPrincipal());
    List<Long> principals = principalResolver.principalsOf(design.getProject().getId());
    if (routeToPrincipal) {
      if (principals.isEmpty()) {
        throw new ApplicationException(
            "No Principal is assigned to this Project. Assign the Principal role before routing an"
                + " approval to them.");
      }
      if (principals.stream().allMatch(id -> id.equals(requesterId))) {
        throw new ApplicationException(
            "You are the Principal for this Project, so this approval cannot be routed onwards.");
      }
    }

    ApprovalRequest approval = new ApprovalRequest();
    approval.setDesign(design);
    approval.setFile(file);
    approval.setVersion(current);
    approval.setRequesterId(requesterId);
    approval.setStatus(ApprovalStatus.PENDING);
    approval.setCurrentStage(ApprovalStage.INITIAL);
    approval.setRouteToPrincipal(routeToPrincipal);
    approval.setResubmission(previousEndedInEditRequest(fileId));
    approval.setCreatedBy(requesterId);
    approverIds.forEach(id -> approval.addAssignee(ApprovalAssignee.of(id, ApprovalStage.INITIAL)));

    ApprovalRequest saved = approvalRequestRepo.save(approval);
    saveChecklistSnapshot(saved, applicable, request.checkedItemIds());
    note(saved, ApprovalAction.RAISED, request.note(), current.getRevisionLabel());

    transitionDesign(design, DesignStatus.UNDER_REVIEW);

    Map<Long, UserSummary> users = resolveUsers(approverIds);
    designActivityService.record(
        design,
        ActivityAction.STATUS_CHANGED,
        "Submitted '"
            + file.getDisplayName()
            + "' ("
            + current.getRevisionLabel()
            + ") for approval to "
            + approverIds.stream().map(id -> nameOf(users, id)).collect(Collectors.joining(", ")));

    log.info(
        "Raised approvalId={} fileId={} revision={}",
        saved.getId(),
        fileId,
        current.getRevisionLabel());
    return apiResponseAdaptor.success("File sent for approval successfully.", toView(saved));
  }

  // ── decide ────────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> decide(Long requestId, ApprovalDto.DecisionRequest request) {
    ApprovalRequest approval = requireRequest(requestId);
    Long actorId = requireCurrentUserId();

    if (approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ResourceInUseException(
          "This Approval Request has already been updated. Refresh to view its latest status.");
    }

    ApprovalAssignee assignee =
        approval.activeAssignees(approval.getCurrentStage()).stream()
            .filter(a -> Objects.equals(a.getUserId(), actorId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApplicationException(
                        "Only the assigned Approver can take action on this Approval Request."));

    if (assignee.getDecision() != ApproverDecision.PENDING) {
      throw new ResourceInUseException(
          "You have already completed your decision for this Approval Request.");
    }

    ApproverDecision decision = request.decision();
    String note = trimToNull(request.note());

    if (decision == ApproverDecision.EDIT_REQUESTED && note == null) {
      throw new ApplicationException("Enter a note explaining the required edit.");
    }
    if (decision == ApproverDecision.REJECTED && note == null) {
      throw new ApplicationException("Enter a reason for rejecting this file.");
    }
    if (decision == ApproverDecision.APPROVED
        && designFileCommentRepo.countOpenForFile(approval.getFile().getId()) > 0) {
      // Edit-request and reject stay available — only approval is blocked (ONEMEP-40).
      throw new ResourceInUseException(
          "Resolve the open comments on this file before approving it.");
    }

    assignee.setDecision(decision);
    assignee.setDecidedAt(DateUtils.getCurrentUtcTime());
    assignee.setNote(note);
    assignee.setUpdatedBy(actorId);

    String revision = approval.getVersion().getRevisionLabel();
    note(approval, actionFor(decision), note, revision);

    if (decision.closesRequest()) {
      closeRequest(approval, decision);
    } else if (approval.stageComplete()) {
      completeStage(approval);
    }

    approvalRequestRepo.save(approval);
    recordDecisionActivity(approval, decision, actorId);

    return apiResponseAdaptor.success(messageFor(approval, decision), toView(approval));
  }

  /** An edit-request or rejection ends the cycle outright, whoever else is still outstanding. */
  private void closeRequest(ApprovalRequest approval, ApproverDecision decision) {
    approval.setStatus(
        decision == ApproverDecision.EDIT_REQUESTED
            ? ApprovalStatus.EDIT_REQUESTED
            : ApprovalStatus.REJECTED);
    approval.setCompletedAt(DateUtils.getCurrentUtcTime());
    transitionDesign(
        approval.getDesign(),
        decision == ApproverDecision.EDIT_REQUESTED
            ? DesignStatus.EDIT_REQUESTED
            : DesignStatus.REJECTED);
  }

  /**
   * Every live approver at this stage has approved. Either hand on to the Principal, or finish.
   *
   * <p>A Principal who already approved as a direct approver is not asked twice — ONEMEP-40 forbids
   * the duplicate stage.
   */
  private void completeStage(ApprovalRequest approval) {
    if (approval.getCurrentStage() == ApprovalStage.INITIAL
        && Boolean.TRUE.equals(approval.getRouteToPrincipal())) {

      List<Long> principals =
          principalResolver.principalsOf(approval.getDesign().getProject().getId());
      Set<Long> alreadyApproved =
          approval.activeAssignees(ApprovalStage.INITIAL).stream()
              .map(ApprovalAssignee::getUserId)
              .collect(Collectors.toSet());
      List<Long> outstanding =
          principals.stream()
              .filter(id -> !alreadyApproved.contains(id))
              .filter(id -> !id.equals(approval.getRequesterId()))
              .toList();

      if (!outstanding.isEmpty()) {
        outstanding.forEach(
            id -> approval.addAssignee(ApprovalAssignee.of(id, ApprovalStage.PRINCIPAL)));
        approval.setCurrentStage(ApprovalStage.PRINCIPAL);
        note(
            approval,
            ApprovalAction.ROUTED_TO_PRINCIPAL,
            "Routed to Principal after initial approval.",
            approval.getVersion().getRevisionLabel());
        return;
      }
    }

    approval.setStatus(ApprovalStatus.APPROVED);
    approval.setCompletedAt(DateUtils.getCurrentUtcTime());
    transitionDesign(approval.getDesign(), DesignStatus.APPROVED);
  }

  // ── recall, reassign, cancel ──────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> recall(Long requestId) {
    ApprovalRequest approval = requireRequest(requestId);
    Long actorId = requireCurrentUserId();

    if (!Objects.equals(approval.getRequesterId(), actorId)) {
      throw new ApplicationException("Only the requester can recall this Approval Request.");
    }
    if (approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ResourceInUseException(
          "This Approval Request has already been updated and can no longer be recalled.");
    }
    if (approval.anyDecisionTaken()) {
      throw new ResourceInUseException(
          "This Approval Request can no longer be recalled because an Approver has already taken"
              + " action.");
    }

    approval.setStatus(ApprovalStatus.RECALLED);
    approval.setCompletedAt(DateUtils.getCurrentUtcTime());
    approval.setUpdatedBy(actorId);
    note(approval, ApprovalAction.RECALLED, null, approval.getVersion().getRevisionLabel());
    // Optimistic locking settles the race the ticket describes: whichever of recall and decide
    // commits first wins, and the other is told to refresh.
    approvalRequestRepo.save(approval);

    transitionDesign(approval.getDesign(), DesignStatus.IN_PROGRESS);
    designActivityService.record(
        approval.getDesign(),
        ActivityAction.STATUS_CHANGED,
        "Recalled approval request for '" + approval.getFile().getDisplayName() + "'");

    return apiResponseAdaptor.success("Approval Request recalled successfully.", toView(approval));
  }

  @Override
  @Transactional
  public ApiResponse<?> reassign(Long requestId, ApprovalDto.ReassignRequest request) {
    requireAdmin();
    ApprovalRequest approval = requireRequest(requestId);
    Long actorId = requireCurrentUserId();

    if (approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ResourceInUseException("Only Pending Approval Requests can be reassigned.");
    }

    Long newApproverId = request.newApproverId();
    if (Objects.equals(newApproverId, approval.getRequesterId())) {
      throw new ApplicationException("You cannot assign an Approval Request to yourself.");
    }
    if (!eligibleApproverIds(approval.getDesign()).contains(newApproverId)) {
      throw new ApplicationException(
          "One or more selected Approvers are no longer available. Update the Approver selection"
              + " and try again.");
    }

    List<ApprovalAssignee> outstanding =
        approval.activeAssignees(approval.getCurrentStage()).stream()
            .filter(a -> a.getDecision() == ApproverDecision.PENDING)
            .toList();
    if (outstanding.isEmpty()) {
      throw new ResourceInUseException("There is no outstanding Approver to reassign.");
    }

    // Retire rather than edit, so the journey still shows who was originally asked.
    ApprovalAssignee previous = outstanding.get(0);
    previous.setActive(Boolean.FALSE);
    previous.setUpdatedBy(actorId);
    approval.addAssignee(ApprovalAssignee.of(newApproverId, approval.getCurrentStage()));
    approval.setUpdatedBy(actorId);
    approvalRequestRepo.save(approval);

    Map<Long, UserSummary> users = resolveUsers(List.of(previous.getUserId(), newApproverId));
    String detail =
        "Approval reassigned from "
            + nameOf(users, previous.getUserId())
            + " to "
            + nameOf(users, newApproverId);
    note(approval, ApprovalAction.REASSIGNED, detail, approval.getVersion().getRevisionLabel());
    designActivityService.record(approval.getDesign(), ActivityAction.STATUS_CHANGED, detail);

    return apiResponseAdaptor.success(
        "Approval Request reassigned successfully.", toView(approval));
  }

  @Override
  @Transactional
  public ApiResponse<?> cancel(Long requestId, ApprovalDto.CancelRequest request) {
    requireAdmin();
    ApprovalRequest approval = requireRequest(requestId);
    Long actorId = requireCurrentUserId();

    if (approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ResourceInUseException("Only Pending Approval Requests can be cancelled.");
    }

    approval.setStatus(ApprovalStatus.CANCELLED);
    approval.setCompletedAt(DateUtils.getCurrentUtcTime());
    approval.setUpdatedBy(actorId);
    note(
        approval,
        ApprovalAction.CANCELLED,
        trimToNull(request == null ? null : request.note()),
        approval.getVersion().getRevisionLabel());
    approvalRequestRepo.save(approval);

    transitionDesign(approval.getDesign(), DesignStatus.IN_PROGRESS);
    designActivityService.record(
        approval.getDesign(),
        ActivityAction.STATUS_CHANGED,
        "Approval request cancelled for '" + approval.getFile().getDisplayName() + "'");

    return apiResponseAdaptor.success("Approval Request cancelled successfully.", toView(approval));
  }

  // ── reads ─────────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> journeyForFile(Long fileId) {
    DesignFile file = requireFile(fileId);
    List<ApprovalRequest> requests = approvalRequestRepo.findJourneyForFile(fileId);

    if (requests.isEmpty()) {
      return apiResponseAdaptor.success(
          "No approval has been raised for this file yet.",
          new ApprovalDto.FileJourney(fileId, file.getDisplayName(), null, 0, null, List.of()));
    }

    ApprovalRequest latest = requests.get(0);
    return apiResponseAdaptor.success(
        "Approval journey fetched successfully.",
        new ApprovalDto.FileJourney(
            fileId,
            file.getDisplayName(),
            latest.getVersion().getRevisionLabel(),
            requests.size(),
            latest.getStatus(),
            requests.stream().map(this::toView).toList()));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasPendingApproval(Long fileId) {
    return approvalRequestRepo.hasPendingForFile(fileId);
  }

  // ── checklist ─────────────────────────────────────────────────────────────

  private List<ChecklistMaster> applicableChecklists(Design design) {
    return checklistMasterRepo.findApplicable(
        design.getDiscipline().getId(), design.getType().getId(), design.getSubject().getId());
  }

  /**
   * Every applicable item must be ticked. ONEMEP-40 refuses submission otherwise, and re-resolves
   * the applicable set server-side so a stale dialog cannot submit against an outdated checklist.
   */
  private void requireChecklistComplete(List<ChecklistMaster> applicable, List<Long> checkedIds) {
    Set<Long> required =
        applicable.stream()
            .flatMap(c -> c.getItems().stream())
            .map(ChecklistItem::getId)
            .collect(Collectors.toSet());
    if (required.isEmpty()) {
      return;
    }
    Set<Long> checked = checkedIds == null ? Set.of() : Set.copyOf(checkedIds);
    if (!checked.containsAll(required)) {
      throw new ApplicationException(
          "Complete all checklist items before sending the file for approval.");
    }
    if (!required.containsAll(checked)) {
      throw new ApplicationException(
          "The associated Checklist has changed. Review the updated Checklist before sending the"
              + " file for approval.");
    }
  }

  /** Copies the ticked checklist into the request as plain text — never a reference. */
  private void saveChecklistSnapshot(
      ApprovalRequest approval, List<ChecklistMaster> applicable, List<Long> checkedIds) {
    Set<Long> checked = checkedIds == null ? Set.of() : Set.copyOf(checkedIds);
    int order = 1;
    List<ApprovalChecklistSnapshot> rows = new ArrayList<>();
    for (ChecklistMaster checklist : applicable) {
      for (ChecklistItem item : checklist.getItems()) {
        ApprovalChecklistSnapshot row = new ApprovalChecklistSnapshot();
        row.setRequest(approval);
        row.setChecklistName(nameOf(checklist));
        row.setItemText(item.getText());
        row.setChecked(checked.contains(item.getId()));
        row.setSortOrder(order++);
        row.setCreatedBy(approval.getRequesterId());
        rows.add(row);
      }
    }
    approvalChecklistSnapshotRepo.saveAll(rows);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private boolean previousEndedInEditRequest(Long fileId) {
    return approvalRequestRepo
        .findLatestForFile(fileId)
        .map(r -> r.getStatus() == ApprovalStatus.EDIT_REQUESTED)
        .orElse(false);
  }

  private void transitionDesign(Design design, DesignStatus status) {
    if (design.getStatus() == status) {
      return;
    }
    design.setStatus(status);
    designRepo.save(design);
  }

  private void note(
      ApprovalRequest approval, ApprovalAction action, String text, String revisionLabel) {
    ApprovalNote entry = new ApprovalNote();
    entry.setRequest(approval);
    entry.setAction(action);
    entry.setNote(trimToNull(text));
    entry.setRevisionLabel(revisionLabel);
    entry.setCreatedBy(currentUserId());
    approvalNoteRepo.save(entry);
  }

  private void recordDecisionActivity(
      ApprovalRequest approval, ApproverDecision decision, Long actorId) {
    Map<Long, UserSummary> users = resolveUsers(List.of(actorId));
    String actor = nameOf(users, actorId);
    String file = approval.getFile().getDisplayName();
    String detail =
        switch (decision) {
          case APPROVED -> actor + " approved '" + file + "'";
          case EDIT_REQUESTED -> actor + " requested an edit on '" + file + "'";
          case REJECTED -> actor + " rejected '" + file + "'";
          case PENDING -> actor + " updated '" + file + "'";
        };
    designActivityService.record(approval.getDesign(), ActivityAction.STATUS_CHANGED, detail);
  }

  private static ApprovalAction actionFor(ApproverDecision decision) {
    return switch (decision) {
      case APPROVED -> ApprovalAction.APPROVED;
      case EDIT_REQUESTED -> ApprovalAction.EDIT_REQUESTED;
      case REJECTED -> ApprovalAction.REJECTED;
      case PENDING -> throw new ApplicationException("A decision is required.");
    };
  }

  private static String messageFor(ApprovalRequest approval, ApproverDecision decision) {
    if (decision == ApproverDecision.EDIT_REQUESTED) {
      return "Edit requested successfully.";
    }
    if (decision == ApproverDecision.REJECTED) {
      return "File rejected successfully.";
    }
    if (approval.getStatus() == ApprovalStatus.APPROVED) {
      return "File approved successfully.";
    }
    if (approval.getCurrentStage() == ApprovalStage.PRINCIPAL) {
      return "Approved. The request has been routed to the Principal.";
    }
    return "Approved. The request is still pending with other Approvers.";
  }

  private Set<Long> eligibleApproverIds(Design design) {
    return projectMemberMappingRepo.findByProject_Id(design.getProject().getId()).stream()
        .map(ProjectMemberMapping::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Administrative authority for reassign and cancel.
   *
   * <p>The identity service's exact authority naming has not been confirmed, so both the Spring
   * convention and the bare role name are accepted. Narrow this once the claim shape is known, and
   * prefer {@code @PreAuthorize} if method security is enabled service-wide.
   */
  private void requireAdmin() {
    boolean admin =
        SecurityUtils.getAuthentication()
            .map(
                auth ->
                    auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ADMIN".equals(a)))
            .orElse(false);
    if (!admin) {
      throw new ApplicationException("Only an administrator can perform this action.");
    }
  }

  private static List<Long> distinct(List<Long> ids) {
    return ids == null
        ? List.of()
        : new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
  }

  private DesignFile requireFile(Long fileId) {
    return designFileRepo
        .findById(fileId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "This file is no longer available. Refresh the Design details."));
  }

  private static DesignFileVersion requireCurrentVersion(DesignFile file) {
    DesignFileVersion current = file.getCurrentVersion();
    if (current == null) {
      throw new ResourceNotFoundException(
          "This file has no uploaded version to send for approval.");
    }
    return current;
  }

  private ApprovalRequest requireRequest(Long requestId) {
    return approvalRequestRepo
        .findById(requestId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "This Approval Request is no longer available for action."));
  }

  private static Long currentUserId() {
    return SecurityUtils.getUserId().orElse(null);
  }

  private static Long requireCurrentUserId() {
    return SecurityUtils.getUserId()
        .orElseThrow(() -> new ApplicationException("An authenticated user is required."));
  }

  private Map<Long, UserSummary> resolveUsers(List<Long> ids) {
    List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
    return present.isEmpty() ? Map.of() : userDirectoryClient.resolve(present);
  }

  private String nameOf(Map<Long, UserSummary> users, Long userId) {
    if (userId == null) {
      return "System";
    }
    UserSummary summary = users.get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  private String nameOf(Long userId) {
    return nameOf(resolveUsers(List.of(userId)), userId);
  }

  private static String nameOf(ChecklistMaster checklist) {
    return checklist.getRecordType() == ChecklistRecordType.CHECKLIST ? checklist.getName() : null;
  }

  private static String labelOf(String code, String label) {
    return code + " — " + label;
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isEmpty() ? null : value;
  }

  private ApprovalDto.RequestView toView(ApprovalRequest approval) {
    List<Long> userIds = new ArrayList<>();
    userIds.add(approval.getRequesterId());
    approval.getAssignees().forEach(a -> userIds.add(a.getUserId()));
    List<ApprovalNote> notes = approvalNoteRepo.findForRequest(approval.getId());
    notes.forEach(n -> userIds.add(n.getCreatedBy()));
    Map<Long, UserSummary> users = resolveUsers(userIds);

    List<ApprovalDto.AssigneeView> assignees =
        approval.getAssignees().stream()
            .map(
                a ->
                    new ApprovalDto.AssigneeView(
                        a.getUserId(),
                        nameOf(users, a.getUserId()),
                        a.getStage(),
                        a.getDecision(),
                        a.getDecidedAt(),
                        a.getNote(),
                        Boolean.TRUE.equals(a.getActive())))
            .toList();

    List<ApprovalAssignee> live = approval.activeAssignees(approval.getCurrentStage());
    int approved =
        (int) live.stream().filter(a -> a.getDecision() == ApproverDecision.APPROVED).count();

    return new ApprovalDto.RequestView(
        approval.getId(),
        approval.getFile().getId(),
        approval.getFile().getDisplayName(),
        approval.getVersion().getId(),
        approval.getVersion().getRevisionLabel(),
        approval.getStatus(),
        approval.getCurrentStage(),
        Boolean.TRUE.equals(approval.getResubmission()),
        Boolean.TRUE.equals(approval.getRouteToPrincipal()),
        nameOf(users, approval.getRequesterId()),
        approval.getRequesterId(),
        approval.getCreatedDate(),
        approval.getCompletedAt(),
        approved,
        live.size(),
        assignees,
        approvalChecklistSnapshotRepo.findForRequest(approval.getId()).stream()
            .map(
                s ->
                    new ApprovalDto.ChecklistSnapshotView(
                        s.getChecklistName(), s.getItemText(), Boolean.TRUE.equals(s.getChecked())))
            .toList(),
        notes.stream()
            .map(
                n ->
                    new ApprovalDto.NoteView(
                        n.getId(),
                        n.getAction(),
                        n.getNote(),
                        n.getRevisionLabel(),
                        nameOf(users, n.getCreatedBy()),
                        n.getCreatedBy(),
                        n.getCreatedDate()))
            .toList());
  }
}
