package com.netlink.onemep_feature.project.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An uploaded specs sheet for a project (ONEMEP-15). Bytes are stored in-row; {@code fileData} is
 * lazily fetched so metadata listings never load the payload.
 */
@Entity
@Table(name = "project_spec_sheet")
@Getter
@Setter
public class ProjectSpecSheet extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private ProjectMaster project;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "file_extension", nullable = false)
  private String fileExtension;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  // Mapped to Postgres BYTEA (not @Lob/oid); lazily fetched so metadata listings skip the bytes.
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "file_data", nullable = false)
  private byte[] fileData;
}
