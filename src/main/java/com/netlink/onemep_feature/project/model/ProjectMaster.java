package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.detailinglevel.model.DetailingLevelMaster;
import com.netlink.onemep_feature.handlingoffice.model.HandlingOfficeMaster;
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
  private String lifecycleStatus = "ACTIVE";

  @Column(name = "priority", nullable = false)
  private String priority = "MEDIUM";

  /** CONFIRMED or NON_CONFIRMED (pursuit). Non-confirmed projects use an NC-prefixed number. */
  @Column(name = "project_type", nullable = false)
  private String type = "NON_CONFIRMED";

  /**
   * Once a project is CONFIRMED, its type and Project ID are locked and can no longer change (the
   * Non-confirmed → Confirmed transition is irreversible).
   */
  @Column(name = "type_locked", nullable = false)
  private Boolean typeLocked = Boolean.FALSE;

  @Column(name = "client")
  private String client;

  @Column(name = "location")
  private String location;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "handling_office_id")
  private HandlingOfficeMaster handlingOffice;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "detailing_level_id")
  private DetailingLevelMaster detailingLevel;

  @Column(name = "description")
  private String description;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
