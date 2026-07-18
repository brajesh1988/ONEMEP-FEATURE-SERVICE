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
 * A stakeholder in a project's directory — project head, architect, structure, etc. (ONEMEP-15).
 */
@Entity
@Table(name = "project_stakeholder")
@Getter
@Setter
public class ProjectStakeholder extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "organization")
  private String organization;

  @Column(name = "email")
  private String email;

  @Column(name = "phone")
  private String phone;
}
