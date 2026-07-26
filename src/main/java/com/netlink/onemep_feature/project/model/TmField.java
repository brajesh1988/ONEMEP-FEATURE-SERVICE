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
 * A single field in the Technical Master catalog (PTMS field dictionary, ONEMEP-29). Seeded from
 * the official spreadsheet in V8. Which categories use a field is held in {@code tm_field_category}
 * (keyed by the category's {@code series_code}); a project's sheet is the fields for its category.
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

  @Column(name = "section", nullable = false)
  private String section;

  @Column(name = "section_order", nullable = false)
  private Integer sectionOrder;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "field_key", nullable = false)
  private String fieldKey;

  @Column(name = "unit")
  private String unit;

  /** NUMBER or TEXT (no dropdowns exist in the sheet). */
  @Column(name = "data_type", nullable = false)
  private String dataType;

  /** YES = auto-fed to calculators/AI skills; REF = reference only. */
  @Column(name = "feeds", nullable = false)
  private String feeds;

  /** Core = locked field the skills depend on (treated as required in the UI). */
  @Column(name = "core", nullable = false)
  private Boolean core = Boolean.FALSE;

  @Column(name = "notes")
  private String notes;

  @Column(name = "field_order", nullable = false)
  private Integer fieldOrder;
}
