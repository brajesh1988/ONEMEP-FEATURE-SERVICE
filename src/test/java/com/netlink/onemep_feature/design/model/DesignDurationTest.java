package com.netlink.onemep_feature.design.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Duration is derived from the two dates and never stored (ONEMEP-38). */
class DesignDurationTest {

  @Test
  void durationDays_matchesTheTicketsWorkedExample() {
    // "Start Date: 23 June, Due Date: 10 July, Duration: 17 days"
    assertThat(design(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 7, 10)).durationDays())
        .contains(17L);
  }

  @Test
  void durationDays_isZeroWhenStartAndDueAreTheSameDay() {
    LocalDate day = LocalDate.of(2026, 6, 23);
    assertThat(design(day, day).durationDays()).contains(0L);
  }

  @Test
  void durationDays_isEmptyWhenEitherDateIsMissing() {
    assertThat(design(LocalDate.of(2026, 6, 23), null).durationDays()).isEmpty();
    assertThat(design(null, LocalDate.of(2026, 7, 10)).durationDays()).isEmpty();
    assertThat(design(null, null).durationDays()).isEmpty();
  }

  @Test
  void reminder_resolvesRelativeToTheCurrentDueDate() {
    LocalDate due = LocalDate.of(2026, 7, 10);

    assertThat(TaskReminder.ON_DUE_DATE.dueOn(due)).contains(due);
    assertThat(TaskReminder.ONE_DAY_BEFORE.dueOn(due)).contains(LocalDate.of(2026, 7, 9));
    assertThat(TaskReminder.THREE_DAYS_BEFORE.dueOn(due)).contains(LocalDate.of(2026, 7, 7));
    assertThat(TaskReminder.ONE_WEEK_BEFORE.dueOn(due)).contains(LocalDate.of(2026, 7, 3));
  }

  @Test
  void reminder_isUnsetWithoutADueDateOrWhenNone() {
    assertThat(TaskReminder.ONE_WEEK_BEFORE.dueOn(null)).isEqualTo(Optional.empty());
    assertThat(TaskReminder.NONE.dueOn(LocalDate.of(2026, 7, 10))).isEqualTo(Optional.empty());
  }

  private static Design design(LocalDate start, LocalDate due) {
    Design d = new Design();
    d.setStartDate(start);
    d.setDueDate(due);
    return d;
  }
}
