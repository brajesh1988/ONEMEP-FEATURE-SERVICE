package com.netlink.onemep_feature.designimport.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** One submitted import job: the unit the caller receives an id for and later polls (ONEMEP-35). */
@Entity
@Table(name = "design_import_batch")
@Getter
@Setter
public class DesignImportBatch extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ImportStatus status = ImportStatus.READY;

  @Column(name = "total_files", nullable = false)
  private Integer totalFiles = 0;

  @Column(name = "total_rows", nullable = false)
  private Integer totalRows = 0;

  @Column(name = "imported_rows", nullable = false)
  private Integer importedRows = 0;

  @Column(name = "failed_rows", nullable = false)
  private Integer failedRows = 0;

  @Column(name = "summary", length = 500)
  private String summary;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;
}
