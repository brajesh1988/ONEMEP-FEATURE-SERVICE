package com.netlink.onemep_feature.unit.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "unit_master")
@Getter
@Setter
public class UnitMaster extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "symbol", nullable = false)
  private String symbol;

  /** One of INTEGER, DECIMAL, TEXT, BOOLEAN (enforced by DB CHECK + service validation). */
  @Column(name = "accepted_input_type", nullable = false)
  private String acceptedInputType;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
