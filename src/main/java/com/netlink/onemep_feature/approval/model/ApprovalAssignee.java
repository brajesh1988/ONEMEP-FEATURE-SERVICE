package com.netlink.onemep_feature.approval.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One approver's assignment on one stage of a request (ONEMEP-40).
 *
 * <p>A reassignment retires the row by clearing {@link #active} and adds a new one, so the journey
 * still shows who was originally asked — the ticket requires that history to survive.
 */
@Entity
@Table(name = "approval_assignee")
@Getter
@Setter
public class ApprovalAssignee extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false, updatable = false)
  private ApprovalRequest request;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "stage", nullable = false, length = 20, updatable = false)
  private ApprovalStage stage;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision", nullable = false, length = 20)
  private ApproverDecision decision = ApproverDecision.PENDING;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "note", length = 1000)
  private String note;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;

  public static ApprovalAssignee of(Long userId, ApprovalStage stage) {
    ApprovalAssignee assignee = new ApprovalAssignee();
    assignee.setUserId(userId);
    assignee.setStage(stage);
    return assignee;
  }
}
