package com.netlink.onemep_feature.file.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.common.storage.StorageKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One immutable revision of a logical file (ONEMEP-39).
 *
 * <p>Every column is {@code updatable = false}. A new upload appends a row; nothing ever rewrites
 * an existing one, which is what makes "previous versions shall not be overwritten" a property of
 * the mapping rather than a rule someone has to remember.
 */
@Entity
@Table(name = "design_file_version")
@Getter
@Setter
public class DesignFileVersion extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "file_id", nullable = false, updatable = false)
  private DesignFile file;

  @Column(name = "revision_no", nullable = false, updatable = false)
  private Integer revisionNo;

  /** Display form of {@link #revisionNo}: {@code R0}, {@code R1}, … */
  @Column(name = "revision_label", nullable = false, length = 10, updatable = false)
  private String revisionLabel;

  @Column(name = "storage_key", nullable = false, length = 1024, updatable = false)
  private String storageKey;

  @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
  private String originalFilename;

  @Column(name = "file_extension", length = 20, updatable = false)
  private String fileExtension;

  @Column(name = "content_type", length = 150, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private Long sizeBytes;

  @Column(name = "note", length = 1000, updatable = false)
  private String note;

  public StorageKey key() {
    return new StorageKey(storageKey);
  }

  public static String labelFor(int revisionNo) {
    return "R" + revisionNo;
  }
}
