package com.netlink.onemep_feature.design.model;

/**
 * Workflow state of a Design (ONEMEP-38).
 *
 * <p>Owned by the approval flow (ONEMEP-40) — an Approval Request moves a Design to {@link
 * #UNDER_REVIEW}, a decision moves it on again. Never settable from the Add or Edit Design screens,
 * which is why it is an enum in code rather than configurable reference data.
 */
public enum DesignStatus {
  DRAFT,
  IN_PROGRESS,
  UNDER_REVIEW,
  EDIT_REQUESTED,
  REJECTED,
  APPROVED
}
