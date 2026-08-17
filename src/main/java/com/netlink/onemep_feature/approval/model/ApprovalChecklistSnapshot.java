package com.netlink.onemep_feature.approval.model;

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
 * One checklist item as it stood when the request was raised (ONEMEP-40).
 *
 * <p>Deliberately a text copy with no reference back to the Checklist Master. Editing or deleting
 * the master afterwards must leave historic requests untouched, which is only true if nothing
 * points at it.
 */
@Entity
@Table(name = "approval_checklist_snapshot")
@Getter
@Setter
public class ApprovalChecklistSnapshot extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false, updatable = false)
  private ApprovalRequest request;

  /** Null when the item came from a Single Item record. */
  @Column(name = "checklist_name", length = 50, updatable = false)
  private String checklistName;

  @Column(name = "item_text", nullable = false, length = 250, updatable = false)
  private String itemText;

  @Column(name = "checked", nullable = false, updatable = false)
  private Boolean checked = Boolean.FALSE;

  @Column(name = "sort_order", nullable = false, updatable = false)
  private Integer sortOrder;
}
