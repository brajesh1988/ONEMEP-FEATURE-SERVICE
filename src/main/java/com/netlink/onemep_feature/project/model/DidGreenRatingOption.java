package com.netlink.onemep_feature.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Configured options for the DID "Green rating target" field. Seeded, not user-editable yet. */
@Entity
@Table(name = "did_green_rating_option")
@Getter
@Setter
public class DidGreenRatingOption {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "option_order", nullable = false)
  private Integer optionOrder;

  @Column(name = "active", nullable = false)
  private Boolean active = Boolean.TRUE;
}
