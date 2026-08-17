package com.netlink.onemep_feature.checklist.model;

/**
 * Whether a master record is a named multi-item Checklist or a standalone Single Item. Fixed at
 * creation — ONEMEP-34 forbids converting between the two, in the backend as well as the UI.
 */
public enum ChecklistRecordType {
  CHECKLIST,
  SINGLE_ITEM
}
