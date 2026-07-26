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
 * A filled value for one Technical Master field on a project (ONEMEP-29). Keyed by the catalog
 * field's {@code field_key}; one row per field with a value, replaced wholesale on save.
 */
@Entity
@Table(name = "project_technical_field_value")
@Getter
@Setter
public class ProjectTechnicalFieldValue extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_master_id", nullable = false, updatable = false)
  private ProjectTechnicalMaster technicalMaster;

  @Column(name = "field_key", nullable = false)
  private String fieldKey;

  @Column(name = "value")
  private String value;
}
