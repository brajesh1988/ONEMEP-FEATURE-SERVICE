package com.netlink.onemep_feature.approval.controller;

import com.netlink.onemep_feature.approval.dto.ApprovalDto;
import com.netlink.onemep_feature.approval.service.ApprovalService;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * File approval flow (ONEMEP-40).
 *
 * <p>Every state change is a POST to a named action rather than a PATCH of a status field — the
 * transitions are guarded, and a client must not be able to name the state it wants.
 */
@RestController
@RequiredArgsConstructor
public class ApprovalController {

  private final ApprovalService approvalService;

  @Operation(
      summary = "Fetch what the Send for Approval dialog needs",
      tags = {"Approvals"})
  @GetMapping("/files/{fileId}/approval-context")
  public ResponseEntity<ApiResponse<?>> context(@PathVariable @NotNull Long fileId) {
    return ResponseEntity.ok(approvalService.submissionContext(fileId));
  }

  @Operation(
      summary = "Send a file's current revision for approval",
      tags = {"Approvals"})
  @PostMapping("/files/{fileId}/approval-requests")
  public ResponseEntity<ApiResponse<?>> submit(
      @PathVariable @NotNull Long fileId, @Valid @RequestBody ApprovalDto.SubmitRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(approvalService.submit(fileId, request));
  }

  @Operation(
      summary = "Fetch a file's approval journey",
      tags = {"Approvals"})
  @GetMapping("/files/{fileId}/approval-requests")
  public ResponseEntity<ApiResponse<?>> journey(@PathVariable @NotNull Long fileId) {
    return ResponseEntity.ok(approvalService.journeyForFile(fileId));
  }

  /** Approve, request edit, or reject — one guarded transition. */
  @Operation(
      summary = "Record an approver's decision",
      tags = {"Approvals"})
  @PostMapping("/approval-requests/{requestId}/decisions")
  public ResponseEntity<ApiResponse<?>> decide(
      @PathVariable @NotNull Long requestId,
      @Valid @RequestBody ApprovalDto.DecisionRequest request) {
    return ResponseEntity.ok(approvalService.decide(requestId, request));
  }

  @Operation(
      summary = "Recall a pending approval request",
      tags = {"Approvals"})
  @PostMapping("/approval-requests/{requestId}/recall")
  public ResponseEntity<ApiResponse<?>> recall(@PathVariable @NotNull Long requestId) {
    return ResponseEntity.ok(approvalService.recall(requestId));
  }

  @Operation(
      summary = "Reassign a pending approval request to another approver",
      tags = {"Approvals"})
  @PostMapping("/approval-requests/{requestId}/reassign")
  public ResponseEntity<ApiResponse<?>> reassign(
      @PathVariable @NotNull Long requestId,
      @Valid @RequestBody ApprovalDto.ReassignRequest request) {
    return ResponseEntity.ok(approvalService.reassign(requestId, request));
  }

  @Operation(
      summary = "Cancel a pending approval request",
      tags = {"Approvals"})
  @PostMapping("/approval-requests/{requestId}/cancel")
  public ResponseEntity<ApiResponse<?>> cancel(
      @PathVariable @NotNull Long requestId,
      @RequestBody(required = false) ApprovalDto.CancelRequest request) {
    return ResponseEntity.ok(approvalService.cancel(requestId, request));
  }
}
