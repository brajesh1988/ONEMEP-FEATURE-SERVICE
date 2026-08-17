package com.netlink.onemep_feature.approval.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;

/**
 * Central Approval Listing (ONEMEP-44).
 *
 * <p>Strictly read-only. The ticket puts every decision, recall and administrative action out of
 * scope for this screen — it shows state and navigates to the Design, nothing more. There are
 * deliberately no write methods here, and the rows it returns are the same {@code ApprovalRequest}
 * records the Design Detail screen uses, never copies.
 */
public interface ApprovalListingService {

  /** Paged rows for one tab. Tab comes from {@code filters.tab}; defaults to Pending. */
  ApiResponse<?> list(GenericListRequestDTO request);

  /** Tab counts and the sidebar badge. */
  ApiResponse<?> summary();
}
