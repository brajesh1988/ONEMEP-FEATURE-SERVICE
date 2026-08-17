package com.netlink.onemep_feature.lookup.model;

import java.util.Optional;

/**
 * Catalogues held in {@code lookup_value}. The name of each constant is the persisted {@code
 * lookup_type} and must stay in step with the {@code ck_lookup_type} check constraint (V16).
 */
public enum LookupType {
  DISCIPLINE,
  DESIGN_TYPE,
  SUBJECT,
  FLOOR,
  ZONE,
  STAGE;

  /** Resolves a path/query value such as {@code "discipline"} or {@code "design-type"}. */
  public static Optional<LookupType> from(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toUpperCase().replace('-', '_');
    for (LookupType type : values()) {
      if (type.name().equals(normalized)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
