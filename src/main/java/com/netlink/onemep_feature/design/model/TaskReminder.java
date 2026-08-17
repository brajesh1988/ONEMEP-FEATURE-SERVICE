package com.netlink.onemep_feature.design.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * When to remind the owner, relative to the Due Date (ONEMEP-38).
 *
 * <p>Stored as an offset rather than a computed date so the reminder follows the Due Date when it
 * moves — the ticket requires exactly that ("If the Due Date changes, the reminder schedule shall
 * use the updated Due Date").
 */
public enum TaskReminder {
  NONE(null),
  ON_DUE_DATE(0),
  ONE_DAY_BEFORE(1),
  THREE_DAYS_BEFORE(3),
  ONE_WEEK_BEFORE(7);

  private final Integer daysBefore;

  TaskReminder(Integer daysBefore) {
    this.daysBefore = daysBefore;
  }

  /** The date this reminder falls due, or empty when unset or the Design has no Due Date. */
  public Optional<LocalDate> dueOn(LocalDate dueDate) {
    if (daysBefore == null || dueDate == null) {
      return Optional.empty();
    }
    return Optional.of(dueDate.minusDays(daysBefore));
  }
}
