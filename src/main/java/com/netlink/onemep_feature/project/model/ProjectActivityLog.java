package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only per-project audit entry (ONEMEP-15). The actor and timestamp come from the inherited
 * {@code createdBy}/{@code createdDate} audit columns.
 */
@Entity
@Table(name = "project_activity_log")
@Getter
@Setter
public class ProjectActivityLog extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  /** Stable action code, e.g. PROJECT_CREATED, LIFECYCLE_CHANGED, PRIORITY_CHANGED. */
  @Column(name = "action", nullable = false, updatable = false)
  private String action;

  @Column(name = "detail", updatable = false)
  private String detail;

  @Column(name = "reason", updatable = false)
  private String reason;
}
