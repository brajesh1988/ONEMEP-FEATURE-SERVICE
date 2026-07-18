package com.netlink.onemep_feature.teamrole.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.tier.model.TierMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "team_role_master")
@Getter
@Setter
public class TeamRoleMaster extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tier_id", nullable = false)
  private TierMaster tier;

  @Column(name = "is_active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
