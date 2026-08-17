package com.netlink.onemep_feature.activity.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One immutable audit event against a Design.
 *
 * <p>Every column is {@code updatable = false}: ONEMEP-43 requires historic rows never to be
 * rewritten to match current state. Changing a field twice produces two rows, not one edited row.
 */
@Entity
@Table(name = "design_activity_log")
@Getter
@Setter
public class DesignActivityLog extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 50, updatable = false)
  private ActivityAction action;

  @Column(name = "detail", nullable = false, length = 1000, updatable = false)
  private String detail;

  /** Null when the platform acted rather than a person. */
  @Column(name = "actor_label", length = 150, updatable = false)
  private String actorLabel;
}
