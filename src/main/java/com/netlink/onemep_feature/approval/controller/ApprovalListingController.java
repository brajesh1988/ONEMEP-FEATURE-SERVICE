package com.netlink.onemep_feature.approval.controller;

import com.netlink.onemep_feature.approval.service.ApprovalListingService;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Central Approval Listing (ONEMEP-44).
 *
 * <p>Read-only by design. ONEMEP-44 puts approve, reject, request-edit, recall, reassign and cancel
 * explicitly out of scope here — a user acts on the Design Detail screen, and this one only shows
 * state and points at it. The absence of those routes is the requirement, not an omission; the
 * actions live on {@code ApprovalController}.
 */
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalListingController {

  private final ApprovalListingService approvalListingService;

  /** Paged rows for one tab; {@code filters.tab} is PENDING (default) or COMPLETED. */
  @Operation(
      summary = "List the signed-in user's approvals",
      tags = {"Approvals"})
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(approvalListingService.list(request));
  }

  /** Tab counts plus the sidebar badge, which counts only what awaits this user. */
  @Operation(
      summary = "Fetch approval tab counts and the action-required badge",
      tags = {"Approvals"})
  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<?>> summary() {
    return ResponseEntity.ok(approvalListingService.summary());
  }
}
