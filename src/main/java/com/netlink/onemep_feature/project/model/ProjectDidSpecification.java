package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
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
 * A DID (Design Input Data) specification line item on a project's Technical Master (ONEMEP-29).
 * The exact field set is provisional — the story has no schema — so this is modeled as a named
 * specification with an optional unit and remarks. Revisit once the story is detailed.
 */
@Entity
@Table(name = "project_did_specification")
@Getter
@Setter
public class ProjectDidSpecification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_master_id", nullable = false, updatable = false)
  private ProjectTechnicalMaster technicalMaster;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "specification")
  private String specification;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private UnitMaster unit;

  @Column(name = "remarks")
  private String remarks;
}
