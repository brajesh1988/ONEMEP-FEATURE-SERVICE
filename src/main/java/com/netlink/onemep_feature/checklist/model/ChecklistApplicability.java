package com.netlink.onemep_feature.checklist.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.lookup.model.LookupValue;
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
 * One applicability selection. A null {@link #value} means <em>Any</em> for that segment.
 *
 * <p>{@code value_type} mirrors {@code segment} so the composite foreign key can pin the reference
 * to the right catalogue. It is written explicitly rather than derived, and {@code
 * ck_checklist_applicability_pair} rejects the row if the two ever disagree.
 */
@Entity
@Table(name = "checklist_applicability")
@Getter
@Setter
public class ChecklistApplicability extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "checklist_id", nullable = false)
  private ChecklistMaster checklist;

  @Enumerated(EnumType.STRING)
  @Column(name = "segment", nullable = false, length = 40)
  private ApplicabilitySegment segment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "value_id")
  private LookupValue value;

  @Column(name = "value_type", length = 40)
  private String valueType;

  /** Wildcard row for a segment — matches every Design regardless of that segment's value. */
  public static ChecklistApplicability any(ApplicabilitySegment segment) {
    ChecklistApplicability row = new ChecklistApplicability();
    row.setSegment(segment);
    return row;
  }

  /** Specific selection; keeps {@code valueType} in step with the segment. */
  public static ChecklistApplicability of(ApplicabilitySegment segment, LookupValue value) {
    ChecklistApplicability row = new ChecklistApplicability();
    row.setSegment(segment);
    row.setValue(value);
    row.setValueType(segment.name());
    return row;
  }

  public boolean isWildcard() {
    return value == null;
  }
}
