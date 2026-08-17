package com.netlink.onemep_feature.timetracking.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One logged time entry (ONEMEP-42).
 *
 * <p>{@link #workDate} is when the work happened; {@link #loggedAt} is when it was recorded. The
 * ticket returns to that distinction repeatedly — "Logged 7 h on 6 Aug" recorded on 8 Aug — so they
 * are separate fields of separate types and neither substitutes for the other.
 *
 * <p>Entries are never merged. Two logs on one day stay two rows, each with its own note and its
 * own logged timestamp.
 */
@Entity
@Table(name = "design_time_entry")
@Getter
@Setter
public class DesignTimeEntry extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "work_date", nullable = false, updatable = false)
  private LocalDate workDate;

  @Column(name = "hours", nullable = false, precision = 4, scale = 2, updatable = false)
  private BigDecimal hours;

  @Column(name = "note", length = 500, updatable = false)
  private String note;

  @Column(name = "logged_at", nullable = false, updatable = false)
  private LocalDateTime loggedAt;
}
