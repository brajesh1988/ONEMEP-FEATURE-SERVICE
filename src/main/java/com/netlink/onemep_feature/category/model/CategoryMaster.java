package com.netlink.onemep_feature.category.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.common.sequence.NumberSequence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "category_master")
@Getter
@Setter
public class CategoryMaster extends BaseEntity implements NumberSequence {

  /**
   * System-generated. Set in two steps at creation (temp → CAT-{id}); never changed afterwards. The
   * "locked" guarantee is enforced in the service layer, not via {@code updatable=false}, because
   * the final value depends on the generated id.
   */
  @Column(name = "category_number", nullable = false)
  private String categoryNumber;

  @Column(name = "name", nullable = false)
  private String name;

  /** Drives project-number generation; locked after creation. */
  @Column(name = "prefix", nullable = false, updatable = false)
  private String prefix;

  /**
   * Numeric series (legacy). Confirmed project numbers are now built from {@link #prefix} + {@link
   * #lastNumber} (+ {@link #suffix}) via {@code SequenceNumbers}; this field is retained for
   * backward-compatible data and is still surfaced in responses. Unique; locked after creation.
   */
  @Column(name = "series_code", updatable = false)
  private Integer seriesCode;

  /** Free-form category classification (metadata only; not used in code generation). */
  @Column(name = "type")
  private String type;

  /** Optional trailing token appended to a confirmed project number after the running counter. */
  @Column(name = "suffix")
  private String suffix;

  /**
   * Running counter of confirmed projects created under this category. {@code null} until the first
   * confirmed project, then increments by 1 each time. Drives confirmed project numbers via {@code
   * SequenceNumbers} — intentionally NOT {@code updatable=false}, unlike prefix/series.
   */
  @Column(name = "last_number")
  private Integer lastNumber;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
