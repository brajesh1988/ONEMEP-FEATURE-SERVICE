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
 * A project lead. {@code userId} references onemep_dev.user_master (owned by the identity service),
 * so it is stored as a raw id rather than a mapped association across the service boundary.
 */
@Entity
@Table(name = "project_lead_mapping")
@Getter
@Setter
public class ProjectLeadMapping extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private ProjectMaster project;

  @Column(name = "user_id", nullable = false)
  private Long userId;
}
