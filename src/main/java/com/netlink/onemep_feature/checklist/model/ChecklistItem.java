package com.netlink.onemep_feature.checklist.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "checklist_item")
@Getter
@Setter
public class ChecklistItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "checklist_id", nullable = false)
  private ChecklistMaster checklist;

  /** 1-based and contiguous; renumbered whenever the item list is replaced. */
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @Column(name = "text", nullable = false, length = 250)
  private String text;
}
