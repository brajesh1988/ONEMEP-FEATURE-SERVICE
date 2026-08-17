package com.netlink.onemep_feature.approval.model;

/** Which round of approval is outstanding (ONEMEP-40). */
public enum ApprovalStage {
  /** The approvers the requester selected. All must approve. */
  INITIAL,

  /** Created only after the initial stage completes, and only when routing was requested. */
  PRINCIPAL
}
