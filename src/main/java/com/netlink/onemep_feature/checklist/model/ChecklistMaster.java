package com.netlink.onemep_feature.checklist.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A Checklist Master record: either a named multi-item Checklist or an unnamed Single Item.
 *
 * <p>Carries an explicit {@link Version} because ONEMEP-34 requires concurrent edits to be detected
 * rather than silently overwritten — "This record has been updated by another user."
 */
@Entity
@Table(name = "checklist_master")
@Getter
@Setter
public class ChecklistMaster extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "record_type", nullable = false, updatable = false)
  private ChecklistRecordType recordType;

  /** Null for a Single Item — enforced by {@code ck_checklist_name_by_type}. */
  @Column(name = "name", length = 50)
  private String name;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  @OneToMany(
      mappedBy = "checklist",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = jakarta.persistence.FetchType.LAZY)
  @OrderBy("sortOrder ASC")
  private List<ChecklistItem> items = new ArrayList<>();

  @OneToMany(
      mappedBy = "checklist",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = jakarta.persistence.FetchType.LAZY)
  private List<ChecklistApplicability> applicability = new ArrayList<>();

  /**
   * Reconciles the item list against {@code texts}, keeping {@code sort_order} contiguous from 1.
   *
   * <p>Rows are updated <em>in place</em> rather than cleared and recreated. Clearing first would
   * make Hibernate issue the new INSERTs before the pending DELETEs within the same flush, and the
   * re-used {@code sort_order} values would collide with {@code uq_checklist_item_order}. Editing
   * in place also leaves the audit columns of unchanged rows alone.
   */
  public void replaceItems(List<String> texts) {
    for (int i = 0; i < texts.size(); i++) {
      if (i < items.size()) {
        ChecklistItem existing = items.get(i);
        existing.setSortOrder(i + 1);
        existing.setText(texts.get(i));
      } else {
        ChecklistItem item = new ChecklistItem();
        item.setChecklist(this);
        item.setSortOrder(i + 1);
        item.setText(texts.get(i));
        items.add(item);
      }
    }
    while (items.size() > texts.size()) {
      items.remove(items.size() - 1);
    }
  }

  public void addApplicability(ChecklistApplicability row) {
    row.setChecklist(this);
    applicability.add(row);
  }
}
