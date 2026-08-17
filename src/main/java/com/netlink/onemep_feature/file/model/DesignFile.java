package com.netlink.onemep_feature.file.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A <em>logical</em> file on a Design — one row in the Uploaded Files table regardless of how many
 * revisions it holds (ONEMEP-39).
 *
 * <p>{@link #nextRevisionNo} is an allocator, not a count. It is read and incremented under a
 * pessimistic row lock so two concurrent uploads to the same file are serialised and receive
 * consecutive R-numbers, rather than colliding on one.
 */
@Entity
@Table(name = "design_file")
@Getter
@Setter
public class DesignFile extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;

  @Column(name = "display_name_normalized", nullable = false, length = 255)
  private String displayNameNormalized;

  /** Whichever revision is Current; the rest are Retained. */
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_version_id")
  private DesignFileVersion currentVersion;

  @Column(name = "next_revision_no", nullable = false)
  private Integer nextRevisionNo = 0;

  /** Takes the next R-number and advances the allocator. Callers must hold the row lock. */
  public int allocateRevisionNo() {
    int allocated = nextRevisionNo;
    nextRevisionNo = allocated + 1;
    return allocated;
  }
}
