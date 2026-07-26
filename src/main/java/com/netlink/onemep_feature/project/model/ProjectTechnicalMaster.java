package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Consolidated, project-scoped Technical Master (ONEMEP-29). Exactly one row per project (enforced
 * by {@code uq_tech_master_project}). Holds the general "project technical inputs" ({@code
 * remarks}) and is the aggregate root for parameters, DID specifications, and attachments.
 *
 * <p>Delivery schedule and client information are intentionally NOT stored here — they are surfaced
 * read-only from the project's own data ({@code project_delivery_schedule}, {@code
 * project_master.client/location}) to keep a single source of truth.
 */
@Entity
@Table(name = "project_technical_master")
@Getter
@Setter
public class ProjectTechnicalMaster extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  /** General project technical inputs. Exact field set is provisional (ONEMEP-29 has no schema). */
  @Column(name = "remarks")
  private String remarks;

  /**
   * Optimistic-lock counter for safe concurrent edits. This is concurrency control, NOT business
   * versioning — history/version details are deferred to ONEMEP-30.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
