package com.netlink.onemep_feature.discussion.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
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

/**
 * An in-app notification for one user.
 *
 * <p>{@link #title} and {@link #body} are denormalised prose so the notification still reads
 * correctly after the message it describes has changed.
 */
@Entity
@Table(name = "user_notification")
@Getter
@Setter
public class UserNotification extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 40, updatable = false)
  private NotificationType type;

  @Column(name = "title", nullable = false, length = 200, updatable = false)
  private String title;

  @Column(name = "body", length = 1000, updatable = false)
  private String body;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "design_id", updatable = false)
  private Design design;

  @Column(name = "is_read", nullable = false)
  private Boolean read = Boolean.FALSE;

  @Column(name = "read_at")
  private LocalDateTime readAt;
}
