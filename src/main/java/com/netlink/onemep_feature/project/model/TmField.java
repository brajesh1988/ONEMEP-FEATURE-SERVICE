package com.netlink.onemep_feature.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A field in the Technical Master catalog (ONEMEP-29), belonging to a {@link TmSection} of a
 * category ({@code series_code}). Fully editable: add / rename / toggle active / delete. A
 * project's sheet is the ACTIVE fields of its category; {@code required} fields must be filled
 * before a project's Technical Master can be saved.
 */
@Entity
@Table(name = "tm_field")
@Getter
@Setter
public class TmField {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "section_id", nullable = false)
  private TmSection section;

  @Column(name = "series_code", nullable = false)
  private Integer seriesCode;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "field_key", nullable = false)
  private String fieldKey;

  @Column(name = "unit")
  private String unit;

  /** NUMBER or TEXT. */
  @Column(name = "data_type", nullable = false)
  private String dataType = "TEXT";

  /** Mandatory field — must have a value before the project's Technical Master can be saved. */
  @Column(name = "required", nullable = false)
  private Boolean required = Boolean.FALSE;

  /** YES = auto-fed to calculators/AI skills; REF = reference only. */
  @Column(name = "feeds", nullable = false)
  private String feeds = "REF";

  @Column(name = "notes")
  private String notes;

  @Column(name = "field_order", nullable = false)
  private Integer fieldOrder;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
