package com.netlink.onemep_feature.design.model;

/**
 * How far the Design's work has got (ONEMEP-35 filter, ONEMEP-36 default).
 *
 * <p>Modelled as an enum rather than a {@code lookup_value} catalogue because it is referenced by
 * name in code. ONEMEP-35 hints these may be configurable — move it to the catalogue if the
 * business confirms that, which is a migration plus a column swap.
 */
public enum WorkProgress {
  NOT_STARTED,
  IN_PROGRESS,
  ISSUED,
  COMPLETED
}
