package com.netlink.onemep_feature.designimport.model;

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
 * One reason one row did not import (ONEMEP-35).
 *
 * <p>A row may have several: the Design Number rule and the Title rule are independent, so a row
 * that breaks both is reported against both rather than being collapsed into one message.
 */
@Entity
@Table(name = "design_import_row_error")
@Getter
@Setter
public class DesignImportRowError extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "file_id", nullable = false, updatable = false)
  private DesignImportFile file;

  /** As the user sees it in Excel — the header is row 1. */
  @Column(name = "row_number", nullable = false, updatable = false)
  private Integer rowNumber;

  /** Null for rules that belong to the whole row rather than one column. */
  @Column(name = "column_name", length = 60, updatable = false)
  private String columnName;

  @Column(name = "message", nullable = false, length = 500, updatable = false)
  private String message;
}
