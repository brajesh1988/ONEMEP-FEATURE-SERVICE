package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** DID tab, "Architect Team" subsection — one row per Technical Master. */
@Entity
@Table(name = "project_did_architect_team")
@Getter
@Setter
public class ProjectDidArchitectTeam extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_master_id", nullable = false, updatable = false)
  private ProjectTechnicalMaster technicalMaster;

  @Column(name = "architecture_firm")
  private String architectureFirm;
}
