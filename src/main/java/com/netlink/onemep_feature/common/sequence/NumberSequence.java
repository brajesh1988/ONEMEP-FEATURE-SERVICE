package com.netlink.onemep_feature.common.sequence;

/**
 * Contract for any entity that generates human-readable codes from a {@code prefix} plus a
 * per-entity running number (and an optional {@code suffix}). Implementers expose the running
 * counter so {@link SequenceNumbers} can allocate the next value in a reusable, entity-agnostic
 * way.
 */
public interface NumberSequence {

  String getPrefix();

  String getSuffix();

  /** The last number handed out for this sequence; {@code null} means none yet (next is 1). */
  Integer getLastNumber();

  void setLastNumber(Integer next);
}
