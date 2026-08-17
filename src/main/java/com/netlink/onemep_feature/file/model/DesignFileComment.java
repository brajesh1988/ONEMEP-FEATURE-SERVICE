package com.netlink.onemep_feature.file.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
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

/**
 * A comment against one specific revision.
 *
 * <p>The reference is to the version, never the logical file — uploading a newer revision must not
 * drag existing comments forward onto it.
 */
@Entity
@Table(name = "design_file_comment")
@Getter
@Setter
public class DesignFileComment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, updatable = false)
  private DesignFileVersion version;

  @Column(name = "body", nullable = false, length = 2000)
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private CommentStatus status = CommentStatus.OPEN;
}
