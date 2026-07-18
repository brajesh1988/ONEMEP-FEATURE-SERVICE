package com.netlink.onemep_feature.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Read-only projection over the identity service's {@code onemep_dev.user_master} table.
 *
 * <p>The feature service does not own users — it only needs to (a) confirm a referenced user id
 * exists before wiring it into a project mapping (so callers get a friendly 404 instead of a raw FK
 * violation) and (b) resolve lead email addresses for notifications. Only the columns required for
 * those two concerns are mapped; this entity is never written by this service.
 */
@Entity
@Table(name = "user_master")
@Getter
public class UserAccountRef {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "email")
  private String email;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  /** Convenience display name, falling back to the email when names are missing. */
  public String displayName() {
    String first = firstName == null ? "" : firstName.trim();
    String last = lastName == null ? "" : lastName.trim();
    String full = (first + " " + last).trim();
    return full.isEmpty() ? email : full;
  }
}
