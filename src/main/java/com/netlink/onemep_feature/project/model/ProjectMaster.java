package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_master")
@Getter
@Setter
public class ProjectMaster extends BaseEntity {

  /**
   * System-generated from the category prefix. Set in two steps at creation (temp → PREFIX-{id});
   * never changed afterwards (locked in the service layer, since the value depends on the id).
   */
  @Column(name = "project_number", nullable = false)
  private String projectNumber;

  @Column(name = "name", nullable = false)
  private String name;

  /** Protected after creation — the category cannot be changed on edit. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false, updatable = false)
  private CategoryMaster category;

  @Column(name = "lifecycle_status", nullable = false)
  private String lifecycleStatus = "DRAFT";

  @Column(name = "priority", nullable = false)
  private String priority = "MEDIUM";

  @Column(name = "description")
  private String description;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
