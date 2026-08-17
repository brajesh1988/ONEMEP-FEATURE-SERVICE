package com.netlink.onemep_feature.design.model;

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
 * A free-text label on a Design (ONEMEP-38).
 *
 * <p>{@code label} keeps what the user typed; {@code labelNormalized} is what the uniqueness rule
 * compares, so "Plant Room" and "plant room" cannot both exist on one Design.
 */
@Entity
@Table(name = "design_tag")
@Getter
@Setter
public class DesignTag extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @Column(name = "label", nullable = false, length = 50)
  private String label;

  @Column(name = "label_normalized", nullable = false, length = 50)
  private String labelNormalized;
}
