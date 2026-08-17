package com.netlink.onemep_feature.approval.dto;

import com.netlink.onemep_feature.approval.model.ApprovalAction;
import com.netlink.onemep_feature.approval.model.ApprovalStage;
import com.netlink.onemep_feature.approval.model.ApprovalStatus;
import com.netlink.onemep_feature.approval.model.ApproverDecision;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for the file approval flow (ONEMEP-40). */
public final class ApprovalDto {
  private ApprovalDto() {}

  /**
   * Raising a request.
   *
   * <p>{@code checkedItemIds} carries the checklist items the requester ticked. Every applicable
   * item must be present — the ticket refuses submission otherwise — and the set is copied into an
   * immutable snapshot rather than referenced.
   */
  public record SubmitRequest(
      @NotEmpty(message = "Select at least one Approver.") List<Long> approverIds,
      List<Long> checkedItemIds,
      String note,
      Boolean routeToPrincipal) {}

  /** One approver's answer. A note is mandatory for edit-requested and rejected. */
  public record DecisionRequest(
      @NotNull(message = "A decision is required.") ApproverDecision decision, String note) {}

  public record ReassignRequest(
      @NotNull(message = "Select the new Approver.") Long newApproverId, String note) {}

  public record CancelRequest(String note) {}

  /** Checklist item as offered on the Send for Approval screen, before submission. */
  public record ApplicableItem(Long id, String checklistName, String itemText) {}

  /** What the Send for Approval dialog needs, resolved server-side. */
  public record SubmissionContext(
      Long fileId,
      String fileName,
      Long versionId,
      String revisionLabel,
      String fileExtension,
      String disciplineLabel,
      String typeLabel,
      String subjectLabel,
      List<ApplicableItem> checklistItems,
      List<EligibleApprover> eligibleApprovers,
      boolean principalRoutingAvailable,
      boolean openCommentsBlockApproval) {}

  public record EligibleApprover(Long id, String displayName, boolean principal) {}

  public record AssigneeView(
      Long userId,
      String displayName,
      ApprovalStage stage,
      ApproverDecision decision,
      LocalDateTime decidedAt,
      String note,
      boolean active) {}

  public record NoteView(
      Long id,
      ApprovalAction action,
      String note,
      String revisionLabel,
      String author,
      Long authorId,
      LocalDateTime createdDate) {}

  /** One event in a logical file's approval journey. */
  public record RequestView(
      Long id,
      Long fileId,
      String fileName,
      Long versionId,
      String revisionLabel,
      ApprovalStatus status,
      ApprovalStage currentStage,
      boolean resubmission,
      boolean routeToPrincipal,
      String requestedBy,
      Long requestedById,
      LocalDateTime requestedAt,
      LocalDateTime completedAt,
      int approvedCount,
      int requiredCount,
      List<AssigneeView> assignees,
      List<ChecklistSnapshotView> checklist,
      List<NoteView> notes) {}

  public record ChecklistSnapshotView(String checklistName, String itemText, boolean checked) {}

  /**
   * The single top-level row a file occupies in the Approvals table, however many cycles it had.
   */
  public record FileJourney(
      Long fileId,
      String fileName,
      String latestRevisionLabel,
      int requestCount,
      ApprovalStatus latestStatus,
      List<RequestView> requests) {}
}
