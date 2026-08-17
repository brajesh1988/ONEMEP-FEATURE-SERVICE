package com.netlink.onemep_feature.discussion.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A resolved @mention. One row per distinct user per message. */
@Entity
@Table(name = "design_message_mention")
@Getter
@Setter
public class DesignMessageMention extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false, updatable = false)
  private DesignMessage message;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;
}
