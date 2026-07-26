package com.netlink.onemep_feature.user.client;

import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Resolves identity-owned user ids into display names/emails. The identity service is the sole
 * owner of user data; the feature service reaches it over gRPC on demand.
 */
public interface UserDirectoryClient {

  /**
   * Resolves the given ids to their {@link UserSummary}. Intended for read/display paths: it never
   * throws and returns only the ids it could resolve — callers should fall back to {@link
   * UserSummary#unknown(Long)} for any id absent from the result (which also covers the identity
   * service being unavailable).
   */
  Map<Long, UserSummary> resolve(Collection<Long> ids);

  /**
   * Returns the subset of ids that do not correspond to a real user, for a friendly validation
   * error on write paths. If the identity service is unreachable this returns an empty set (it does
   * not block the write) — the database foreign key to {@code user_master} remains the ultimate
   * guard.
   */
  Set<Long> findMissing(Collection<Long> ids);
}
