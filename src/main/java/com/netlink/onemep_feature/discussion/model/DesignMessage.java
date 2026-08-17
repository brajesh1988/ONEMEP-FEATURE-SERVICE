package com.netlink.onemep_feature.discussion.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * One post in a Design's discussion thread (ONEMEP-41).
 *
 * <p>Distinct from a file comment and from an approval note: this carries no workflow meaning at
 * all. Saying "looks fine to me" here is not an approval.
 */
@Entity
@Table(name = "design_message")
@Getter
@Setter
public class DesignMessage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @Column(name = "body", nullable = false, length = 4000)
  private String body;

  @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<DesignMessageMention> mentions = new ArrayList<>();

  public void mention(Long userId) {
    DesignMessageMention mention = new DesignMessageMention();
    mention.setMessage(this);
    mention.setUserId(userId);
    mentions.add(mention);
  }
}
