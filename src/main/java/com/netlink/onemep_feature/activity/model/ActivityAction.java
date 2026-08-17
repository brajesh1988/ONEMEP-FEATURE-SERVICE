package com.netlink.onemep_feature.activity.model;

/**
 * Stable machine codes for audited events (ONEMEP-43).
 *
 * <p>Never shown to users — the human-readable sentence lives in {@code detail}. Codes exist so the
 * trail stays queryable and filterable even after the wording of a message changes.
 *
 * <p>Only the events the Design Register can currently raise are defined. Later slices add their
 * own constants as they land: file uploads, approval decisions, discussion posts, time entries.
 */
public enum ActivityAction {
  /** The Design was added to the register — the first entry in every Design's trail. */
  DESIGN_CREATED,

  /**
   * The Design was created by the spreadsheet importer (ONEMEP-35) rather than typed in. A distinct
   * code rather than {@link #DESIGN_CREATED} with different prose, so "which of these came from
   * that import" stays a query rather than a text search.
   */
  DESIGN_IMPORTED,

  /** A descriptive field changed. One event per audit-relevant field. */
  DESIGN_UPDATED,

  /** Workflow-driven status transition, written by the approval flow. */
  STATUS_CHANGED,

  // ── Task section (ONEMEP-38) ──────────────────────────────────────────────

  OWNER_CHANGED,
  PRIORITY_CHANGED,
  COMPLETION_CHANGED,
  SCHEDULE_CHANGED,
  REMINDER_CHANGED,
  TAG_ADDED,
  TAG_REMOVED,

  // ── Uploaded files (ONEMEP-39) ────────────────────────────────────────────

  FILE_UPLOADED,
  FILE_VERSION_UPLOADED,
  FILE_DELETED,
  FILE_VERSION_DELETED,
  FILE_COMMENT_ADDED,
  FILE_COMMENT_RESOLVED,

  // ── Discussion and time tracking (ONEMEP-41/42) ───────────────────────────

  DISCUSSION_POSTED,
  TIME_LOGGED,
  TIME_DELETED
}
