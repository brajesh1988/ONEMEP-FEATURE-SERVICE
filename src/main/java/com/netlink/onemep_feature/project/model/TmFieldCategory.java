package com.netlink.onemep_feature.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Applicability of a {@link TmField} to a project category, keyed by the category's {@code
 * series_code} (1 Commercial .. 10 Mixed-Use). Seeded in V8.
 */
@Entity
@Table(name = "tm_field_category")
@IdClass(TmFieldCategory.Key.class)
@Getter
@Setter
public class TmFieldCategory {

  @Id
  @Column(name = "field_id")
  private Long fieldId;

  @Id
  @Column(name = "series_code")
  private Integer seriesCode;

  /** Composite key (field_id, series_code). */
  public static class Key implements Serializable {
    private Long fieldId;
    private Integer seriesCode;

    public Key() {}

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Key key)) {
        return false;
      }
      return Objects.equals(fieldId, key.fieldId) && Objects.equals(seriesCode, key.seriesCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldId, seriesCode);
    }
  }
}
