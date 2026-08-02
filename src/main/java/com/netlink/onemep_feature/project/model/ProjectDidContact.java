package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A contact row shared by the DID Client Information, Architect Team, and Structure Consultant Team
 * subsections; {@link #partyType} discriminates which subsection it belongs to.
 */
@Entity
@Table(name = "project_did_contact")
@Getter
@Setter
public class ProjectDidContact extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "technical_master_id", nullable = false, updatable = false)
  private ProjectTechnicalMaster technicalMaster;

  @Enumerated(EnumType.STRING)
  @Column(name = "party_type", nullable = false, updatable = false)
  private DidPartyType partyType;

  @Column(name = "designation")
  private String designation;

  @Column(name = "name")
  private String name;

  @Column(name = "mail_id")
  private String mailId;

  @Column(name = "contact_no")
  private String contactNo;

  /** True for the seeded Client rows (Project Owner/Head/Coordinator); those cannot be deleted. */
  @Column(name = "is_default_row", nullable = false)
  private Boolean defaultRow = Boolean.FALSE;

  @Column(name = "contact_order", nullable = false)
  private Integer contactOrder = 0;
}
