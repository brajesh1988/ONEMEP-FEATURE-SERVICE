package com.netlink.onemep_feature.common.model;

import com.netlink.onemep_feature.common.util.DateUtils;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared audit base for every persisted entity. Mirrors the identity service's BaseEntity so the
 * two services keep an identical audit contract (id, created/updated by/date).
 */
@MappedSuperclass
@Getter
@Setter
public class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  protected Long id;

  @Column(name = "created_date", updatable = false, nullable = false)
  private LocalDateTime createdDate;

  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_date")
  private LocalDateTime updatedDate;

  @PrePersist
  private void prePersist() {
    this.createdDate = DateUtils.getCurrentUtcTime();
    this.updatedDate = DateUtils.getCurrentUtcTime();
  }

  @PreUpdate
  private void preUpdate() {
    this.updatedDate = DateUtils.getCurrentUtcTime();
  }
}
