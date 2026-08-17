package com.netlink.onemep_feature.designimport.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.common.storage.StorageKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One spreadsheet inside a batch, with its own status and counters (ONEMEP-35). */
@Entity
@Table(name = "design_import_file")
@Getter
@Setter
public class DesignImportFile extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false, updatable = false)
  private DesignImportBatch batch;

  @Column(name = "ordinal", nullable = false, updatable = false)
  private Integer ordinal;

  @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
  private String originalFilename;

  @Column(name = "file_extension", nullable = false, length = 10, updatable = false)
  private String fileExtension;

  @Column(name = "content_type", length = 150)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes = 0L;

  @Column(name = "storage_key", length = 1024)
  private String storageKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ImportStatus status = ImportStatus.READY;

  @Column(name = "total_rows", nullable = false)
  private Integer totalRows = 0;

  @Column(name = "imported_rows", nullable = false)
  private Integer importedRows = 0;

  @Column(name = "failed_rows", nullable = false)
  private Integer failedRows = 0;

  @Column(name = "message", length = 500)
  private String message;

  @Column(name = "errors_truncated", nullable = false)
  private Boolean errorsTruncated = false;

  /** Null until the bytes have actually landed — the row exists before the upload completes. */
  public StorageKey key() {
    return storageKey == null ? null : new StorageKey(storageKey);
  }
}
