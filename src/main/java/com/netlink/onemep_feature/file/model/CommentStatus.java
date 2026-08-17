package com.netlink.onemep_feature.file.model;

/** Whether a file comment still needs addressing (ONEMEP-39). */
public enum CommentStatus {
  /** Counts towards the badge, and blocks final approval (ONEMEP-40). */
  OPEN,

  /** Resolved, but never hidden — historic comments stay visible. */
  CLOSED
}
