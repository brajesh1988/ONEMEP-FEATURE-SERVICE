package com.netlink.onemep_feature.approval.model;

/** One approver's answer (ONEMEP-40). */
public enum ApproverDecision {
  PENDING,
  APPROVED,
  EDIT_REQUESTED,
  REJECTED;

  /** Edit-requested and rejected both close the whole request, not just this approver's part. */
  public boolean closesRequest() {
    return this == EDIT_REQUESTED || this == REJECTED;
  }
}
