package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import com.netlink.onemep_feature.unit.model.UnitMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A single technical parameter captured on a project's Technical Master (ONEMEP-29). {@code scope}
 * separates common project parameters from category-specific ones. Each parameter references a
 * catalog technical field ({@link TechnicalMaster}) — the "reusable" part of the story — plus an
 * optional unit and the captured value.
 */
@Entity
@Table(name = "project_technical_parameter")
@Getter
@Setter
public class ProjectTechnicalParameter extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_master_id", nullable = false, updatable = false)
  private ProjectTechnicalMaster technicalMaster;

  /** COMMON or CATEGORY_SPECIFIC (enforced by DB CHECK + service validation). */
  @Column(name = "scope", nullable = false)
  private String scope;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_field_id", nullable = false)
  private TechnicalMaster technicalField;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private UnitMaster unit;

  @Column(name = "param_value")
  private String value;

  @Column(name = "remarks")
  private String remarks;
}
