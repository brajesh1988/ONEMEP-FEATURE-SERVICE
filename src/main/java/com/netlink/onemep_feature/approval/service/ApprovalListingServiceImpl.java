package com.netlink.onemep_feature.approval.service;

import com.netlink.onemep_feature.approval.dto.ApprovalListingDto;
import com.netlink.onemep_feature.approval.model.ApprovalAssignee;
import com.netlink.onemep_feature.approval.model.ApprovalRequest;
import com.netlink.onemep_feature.approval.model.ApprovalStage;
import com.netlink.onemep_feature.approval.model.ApprovalStatus;
import com.netlink.onemep_feature.approval.model.ApproverDecision;
import com.netlink.onemep_feature.approval.repo.ApprovalRequestRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PaginationAndSortingDTO;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalListingServiceImpl implements ApprovalListingService {

  private final ApprovalRequestRepo approvalRequestRepo;
  private final UserDirectoryClient userDirectoryClient;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    Long userId = requireCurrentUserId();
    ApprovalListingDto.Tab tab = tabOf(request);
    PageRequest pageable = pageable(request);

    // Sorting is fixed by the query rather than caller-supplied: Pending reads newest-raised first,
    // Completed newest-actioned first, and both sort the whole set before paginating.
    Page<ApprovalRequest> page =
        tab == ApprovalListingDto.Tab.PENDING
            ? approvalRequestRepo.findPendingForUser(userId, pageable)
            : approvalRequestRepo.findCompletedForUser(userId, pageable);

    List<ApprovalListingDto.Row> rows = toRows(page.getContent(), userId, tab);

    return apiResponseAdaptor.success(
        page.isEmpty() ? emptyMessage(tab) : "Approvals fetched successfully.",
        new ApprovalListingDto.Page(
            rows, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> summary() {
    Long userId = requireCurrentUserId();
    PageRequest single = PageRequest.of(0, 1);

    return apiResponseAdaptor.success(
        "Approval summary fetched successfully.",
        new ApprovalListingDto.Summary(
            approvalRequestRepo.countAwaitingUser(userId),
            approvalRequestRepo.findPendingForUser(userId, single).getTotalElements(),
            approvalRequestRepo.findCompletedForUser(userId, single).getTotalElements()));
  }

  // ── mapping ───────────────────────────────────────────────────────────────

  private List<ApprovalListingDto.Row> toRows(
      List<ApprovalRequest> requests, Long userId, ApprovalListingDto.Tab tab) {

    // One directory call for the whole page rather than one per row.
    List<Long> userIds = new ArrayList<>();
    requests.forEach(
        r -> {
          userIds.add(r.getRequesterId());
          r.getAssignees().forEach(a -> userIds.add(a.getUserId()));
        });
    Map<Long, UserSummary> users = resolveUsers(userIds);

    return requests.stream().map(r -> toRow(r, userId, tab, users)).toList();
  }

  private ApprovalListingDto.Row toRow(
      ApprovalRequest approval,
      Long userId,
      ApprovalListingDto.Tab tab,
      Map<Long, UserSummary> users) {

    Design design = approval.getDesign();
    boolean mine = Objects.equals(approval.getRequesterId(), userId);
    ApprovalListingDto.Relationship role =
        mine
            ? ApprovalListingDto.Relationship.YOUR_REQUEST
            : ApprovalListingDto.Relationship.TO_REVIEW;

    // Reviewing shows who asked; your own request shows who you are waiting on.
    String counterparty =
        mine
            ? approval.activeAssignees(approval.getCurrentStage()).stream()
                .map(a -> nameOf(users, a.getUserId()))
                .collect(Collectors.joining(", "))
            : nameOf(users, approval.getRequesterId());

    return new ApprovalListingDto.Row(
        approval.getId(),
        design.getId(),
        design.getDesignNumber(),
        design.getTitle(),
        approval.getFile().getId(),
        approval.getFile().getDisplayName(),
        approval.getVersion().getFileExtension(),
        approval.getVersion().getId(),
        // The revision this request was raised against — a newer upload never rewrites it.
        approval.getVersion().getRevisionLabel(),
        design.getProject().getId(),
        design.getProject().getProjectNumber(),
        design.getProject().getName(),
        role,
        counterparty.isBlank() ? "—" : counterparty,
        approval.getStatus(),
        statusLabel(approval, users),
        tab == ApprovalListingDto.Tab.PENDING
            ? approval.getCreatedDate()
            : approval.getCompletedAt());
  }

  /** Display form of the workflow state; the Listing reports it and never computes it. */
  private String statusLabel(ApprovalRequest approval, Map<Long, UserSummary> users) {
    if (approval.getStatus() != ApprovalStatus.PENDING) {
      return humanise(approval.getStatus().name());
    }

    List<ApprovalAssignee> live = approval.activeAssignees(approval.getCurrentStage());
    long approved = live.stream().filter(a -> a.getDecision() == ApproverDecision.APPROVED).count();

    if (approval.getCurrentStage() == ApprovalStage.PRINCIPAL) {
      return "Pending with Principal";
    }
    if (approved > 0) {
      return approved + " of " + live.size() + " Approved";
    }
    if (live.size() == 1) {
      return "Pending with " + nameOf(users, live.get(0).getUserId());
    }
    return "Pending Review";
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ApprovalListingDto.Tab tabOf(GenericListRequestDTO request) {
    if (request == null || request.getFilters() == null) {
      return ApprovalListingDto.Tab.PENDING;
    }
    Object raw = request.getFilters().get("tab");
    return ApprovalListingDto.Tab.from(raw == null ? null : raw.toString());
  }

  private static PageRequest pageable(GenericListRequestDTO request) {
    PaginationAndSortingDTO ps =
        request != null && request.getPaginationAndSorting() != null
            ? request.getPaginationAndSorting()
            : new PaginationAndSortingDTO();
    return PageRequest.of(ps.getPageNumber(), ps.getPageSize());
  }

  private static String emptyMessage(ApprovalListingDto.Tab tab) {
    return tab == ApprovalListingDto.Tab.PENDING
        ? "Nothing pending right now."
        : "Nothing completed yet.";
  }

  /** {@code EDIT_REQUESTED} → "Edit Requested". */
  private static String humanise(String constant) {
    return java.util.Arrays.stream(constant.split("_"))
        .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  private Map<Long, UserSummary> resolveUsers(List<Long> ids) {
    List<Long> present = ids.stream().filter(Objects::nonNull).distinct().toList();
    return present.isEmpty() ? Map.of() : userDirectoryClient.resolve(present);
  }

  private static String nameOf(Map<Long, UserSummary> users, Long userId) {
    if (userId == null) {
      return "—";
    }
    UserSummary summary = users.get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  private static Long requireCurrentUserId() {
    return SecurityUtils.getUserId()
        .orElseThrow(() -> new ApplicationException("An authenticated user is required."));
  }
}
