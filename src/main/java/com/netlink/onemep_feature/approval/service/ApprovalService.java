package com.netlink.onemep_feature.approval.service;

import com.netlink.onemep_feature.approval.dto.ApprovalDto;
import com.netlink.onemep_feature.common.dto.ApiResponse;

/** File approval flow (ONEMEP-40). */
public interface ApprovalService {

  /** Everything the Send for Approval dialog needs, resolved server-side. */
  ApiResponse<?> submissionContext(Long fileId);

  ApiResponse<?> submit(Long fileId, ApprovalDto.SubmitRequest request);

  /** One approver's answer. The only route by which a request changes state. */
  ApiResponse<?> decide(Long requestId, ApprovalDto.DecisionRequest request);

  /** Requester withdraws, permitted only while nobody has acted. */
  ApiResponse<?> recall(Long requestId);

  ApiResponse<?> reassign(Long requestId, ApprovalDto.ReassignRequest request);

  ApiResponse<?> cancel(Long requestId, ApprovalDto.CancelRequest request);

  /** A logical file's complete approval journey, newest request first. */
  ApiResponse<?> journeyForFile(Long fileId);

  /** Backs the file-deletion guard in ONEMEP-39. */
  boolean hasPendingApproval(Long fileId);
}
