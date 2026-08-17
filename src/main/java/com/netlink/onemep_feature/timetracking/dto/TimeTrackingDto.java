package com.netlink.onemep_feature.timetracking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for Design time tracking (ONEMEP-42). */
public final class TimeTrackingDto {
  private TimeTrackingDto() {}

  public record LogRequest(
      @NotNull(message = "Enter the hours spent.") BigDecimal hours,
      @NotNull(message = "Select the date for which the time was worked.") LocalDate workDate,
      @Size(max = 500, message = "Note must not exceed 500 characters.") String note) {}

  /** One individual entry, shown when a day is expanded. Never merged with its neighbours. */
  public record EntryView(
      Long id, BigDecimal hours, String note, LocalDateTime loggedAt, boolean deletable) {}

  /**
   * A User + Work Date row. The same person appears once per day they logged against, which the
   * ticket calls out as expected rather than a bug.
   */
  public record DayGroup(
      Long userId,
      String user,
      boolean currentUser,
      LocalDate workDate,
      BigDecimal totalHours,
      int entryCount,
      List<EntryView> entries) {}

  /**
   * @param people unique contributors, not the number of rows
   */
  public record Summary(BigDecimal totalHours, long people, List<DayGroup> groups) {}
}
