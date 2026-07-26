package com.netlink.onemep_feature.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A head/section in the Technical Master catalog for a project category (ONEMEP-29). Editable:
 * heads can be added, renamed, toggled active/inactive and deleted per category ({@code
 * series_code}).
 */
@Entity
@Table(name = "tm_section")
@Getter
@Setter
public class TmSection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "series_code", nullable = false)
  private Integer seriesCode;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "section_order", nullable = false)
  private Integer sectionOrder;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;

  /** Seeded heads are system (toggle-only); user-added heads are deletable. */
  @Column(name = "is_system", nullable = false)
  private Boolean system = Boolean.TRUE;
}
