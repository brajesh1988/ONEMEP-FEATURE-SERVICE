package com.netlink.onemep_feature.user.dto;

/**
 * Read-only view of a user owned by the identity service, resolved on demand for display. The
 * feature service never stores this — it only holds the user id and enriches to a name at read
 * time.
 */
public record UserSummary(Long id, String displayName, String email) {

  /** Fallback used when the identity service cannot resolve an id (e.g. it is unreachable). */
  public static UserSummary unknown(Long id) {
    return new UserSummary(id, "User #" + id, null);
  }
}
