package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A delivery milestone / deliverable for a project (ONEMEP-15). */
@Entity
@Table(name = "project_delivery_schedule")
@Getter
@Setter
public class ProjectDeliverySchedule extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  @Column(name = "milestone", nullable = false)
  private String milestone;

  @Column(name = "deliverable")
  private String deliverable;

  @Column(name = "planned_date")
  private LocalDate plannedDate;

  @Column(name = "actual_date")
  private LocalDate actualDate;

  @Column(name = "status", nullable = false)
  private String status = "PENDING";

  @Column(name = "remarks")
  private String remarks;

  /** DID tab "Delivery Schedule" stage start date; unused by general ONEMEP-15 milestones. */
  @Column(name = "start_date")
  private LocalDate startDate;

  /** DID tab "Delivery Schedule" stage end date; unused by general ONEMEP-15 milestones. */
  @Column(name = "end_date")
  private LocalDate endDate;

  /** True for rows owned by the DID Delivery Schedule; scopes DID's diff/delete to these rows. */
  @Column(name = "is_did_stage", nullable = false)
  private Boolean didStage = Boolean.FALSE;

  @Column(name = "stage_order")
  private Integer stageOrder;
}
