package com.netlink.onemep_feature.approval.model;

/** Event types in the approval note history (ONEMEP-40). */
public enum ApprovalAction {
  RAISED,
  APPROVED,
  EDIT_REQUESTED,
  REJECTED,
  RECALLED,
  REASSIGNED,
  CANCELLED,
  ROUTED_TO_PRINCIPAL
}
