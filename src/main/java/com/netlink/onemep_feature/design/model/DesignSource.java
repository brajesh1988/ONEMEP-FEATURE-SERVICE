package com.netlink.onemep_feature.design.model;

/**
 * Where the Design came from (ONEMEP-38). Read-only on every screen — set once by whichever path
 * created the record.
 */
public enum DesignSource {
  /** Added through the Add Drawing screen. */
  MANUAL,

  /** Created by the spreadsheet importer (ONEMEP-35). */
  IMPORT
}
