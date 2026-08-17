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
import lombok.Getter;
import lombok.Setter;

/** An append-only entry in a request's note history (ONEMEP-40). Never updated. */
@Entity
@Table(name = "approval_note")
@Getter
@Setter
public class ApprovalNote extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false, updatable = false)
  private ApprovalRequest request;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 30, updatable = false)
  private ApprovalAction action;

  @Column(name = "note", length = 1000, updatable = false)
  private String note;

  /** The revision this event applied to, so the journey stays version-correct. */
  @Column(name = "revision_label", length = 10, updatable = false)
  private String revisionLabel;
}
