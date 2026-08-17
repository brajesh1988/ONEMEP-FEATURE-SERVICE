package com.netlink.onemep_feature.lookup.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One entry in a reference-data catalogue. Consumers reference this by id and pin the type with a
 * composite foreign key, so a column declared for one catalogue cannot hold a row from another.
 */
@Entity
@Table(name = "lookup_value")
@Getter
@Setter
public class LookupValue extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "lookup_type", nullable = false, updatable = false)
  private LookupType lookupType;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
