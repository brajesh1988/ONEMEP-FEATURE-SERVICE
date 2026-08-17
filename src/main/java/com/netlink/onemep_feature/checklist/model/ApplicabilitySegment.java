package com.netlink.onemep_feature.checklist.model;

import com.netlink.onemep_feature.lookup.model.LookupType;

/**
 * The three dimensions a checklist's applicability is declared across.
 *
 * <p>Each constant maps to the {@link LookupType} it draws values from, and the persisted segment
 * string doubles as the type pin on the composite foreign key — which is why the names must stay
 * identical to the lookup types.
 */
public enum ApplicabilitySegment {
  DISCIPLINE(LookupType.DISCIPLINE),
  DESIGN_TYPE(LookupType.DESIGN_TYPE),
  SUBJECT(LookupType.SUBJECT);

  private final LookupType lookupType;

  ApplicabilitySegment(LookupType lookupType) {
    this.lookupType = lookupType;
  }

  public LookupType lookupType() {
    return lookupType;
  }
}
