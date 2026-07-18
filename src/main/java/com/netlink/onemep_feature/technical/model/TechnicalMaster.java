package com.netlink.onemep_feature.technical.model;

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
 * PROVISIONAL entity for ONEMEP-29 (Technical Master). The story has no description yet, so this is
 * modeled minimally: a named technical field whose measurement type is validated by a Unit. Revisit
 * the shape once the story is detailed.
 */
@Entity
@Table(name = "technical_master")
@Getter
@Setter
public class TechnicalMaster extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unit_id")
  private UnitMaster unit;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
