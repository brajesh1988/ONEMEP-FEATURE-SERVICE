package com.netlink.onemep_feature.handlingoffice.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Configured list of handling offices selectable on a project (ONEMEP-13/14/15). */
@Entity
@Table(name = "handling_office_master")
@Getter
@Setter
public class HandlingOfficeMaster extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
