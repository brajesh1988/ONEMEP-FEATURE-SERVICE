package com.netlink.onemep_feature.design.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/**
 * A Design within a Project's register.
 *
 * <p>The six identity segments plus {@link #designNumber} are {@code updatable = false}: ONEMEP-37
 * locks them after creation and requires the backend to enforce that independently of the UI, so
 * Hibernate is not permitted to write them on an update even if a service bug tried to.
 */
@Entity
@Table(name = "design")
@Getter
@Setter
public class Design extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  /** Free-text segment; 'XX' when the user leaves it blank (ONEMEP-36). */
  @Column(name = "zone_code", nullable = false, length = 10, updatable = false)
  private String zoneCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "discipline_id", nullable = false, updatable = false)
  private LookupValue discipline;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "type_id", nullable = false, updatable = false)
  private LookupValue type;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false, updatable = false)
  private LookupValue subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "floor_id", nullable = false, updatable = false)
  private LookupValue floor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "stage_id", nullable = false, updatable = false)
  private LookupValue stage;

  @Column(name = "design_number", nullable = false, length = 120, updatable = false)
  private String designNumber;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  /** Trimmed and lower-cased; the column the uniqueness constraint actually compares. */
  @Column(name = "title_normalized", nullable = false, length = 200)
  private String titleNormalized;

  @Column(name = "sheet_size", length = 10)
  private String sheetSize;

  @Column(name = "scale", length = 30)
  private String scale;

  @Column(name = "prepared_by", length = 150)
  private String preparedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_progress", nullable = false, length = 30)
  private WorkProgress workProgress = WorkProgress.NOT_STARTED;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private DesignStatus status = DesignStatus.DRAFT;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  // ── Task section (ONEMEP-38) ──────────────────────────────────────────────
  // The Design doubles as a project task, so these sit on the same row.

  /** Identity-owned user id; the display name is resolved on demand, never stored. */
  @Column(name = "owner_id")
  private Long ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false, length = 10)
  private TaskPriority priority = TaskPriority.MEDIUM;

  @Column(name = "completion_pct", nullable = false)
  private Integer completionPct = 0;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "reminder", nullable = false, length = 30)
  private TaskReminder reminder = TaskReminder.NONE;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20, updatable = false)
  private DesignSource source = DesignSource.MANUAL;

  /**
   * Days between Start and Due, derived rather than stored (ONEMEP-38). Empty whenever either date
   * is missing, which the UI renders as a dash — never a fabricated zero.
   */
  public Optional<Long> durationDays() {
    if (startDate == null || dueDate == null) {
      return Optional.empty();
    }
    return Optional.of(ChronoUnit.DAYS.between(startDate, dueDate));
  }
}
