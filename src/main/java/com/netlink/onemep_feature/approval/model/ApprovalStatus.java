package com.netlink.onemep_feature.approval.model;

/**
 * Lifecycle state of an Approval Request (ONEMEP-40).
 *
 * <p>{@link #PENDING} is the only open state; everything else is terminal. A terminal request is
 * never reopened — a further cycle means a new request against a newer revision.
 */
public enum ApprovalStatus {
  PENDING,

  /** Every required approver, including the Principal where routed, has approved. */
  APPROVED,

  /** An approver asked for changes. Closes the request; the file needs a new revision. */
  EDIT_REQUESTED,

  /** An approver rejected it. Closes the request; the file needs a new revision. */
  REJECTED,

  /** Withdrawn by the requester before anyone acted. */
  RECALLED,

  /** Withdrawn by an administrator. */
  CANCELLED;

  public boolean isTerminal() {
    return this != PENDING;
  }

  /**
   * Whether reaching this state uses up the revision. A recall or cancellation is not a decision on
   * the file, so the same revision may be submitted again after one.
   */
  public boolean consumesRevision() {
    return this == APPROVED || this == EDIT_REQUESTED || this == REJECTED;
  }
}
