package com.netlink.onemep_feature.category.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "category_master")
@Getter
@Setter
public class CategoryMaster extends BaseEntity {

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

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
