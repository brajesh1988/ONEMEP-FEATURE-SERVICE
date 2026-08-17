package com.netlink.onemep_feature.activity.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.design.model.Design;

/**
 * Design audit trail (ONEMEP-43).
 *
 * <p>{@code record} is what every other feature calls. It has no {@code @Transactional} of its own
 * and therefore joins the caller's transaction — deliberately, and it is the answer to the open
 * question the ticket itself raises ("Whether Activity persistence must be transactionally
 * mandatory for every state-changing operation"):
 *
 * <ul>
 *   <li>a business change that rolls back takes its audit row with it, so a failed action can never
 *       appear as though it happened;
 *   <li>a business change that commits always has its audit row, so the trail cannot silently lose
 *       events the way a best-effort listener can.
 * </ul>
 *
 * <p>The cost is that an audit failure fails the business operation. For an audit-bearing approval
 * system that is the right trade, but it is a trade — revisit if the business says otherwise.
 */
public interface DesignActivityService {

  /** Appends one event. Call after the change has been applied, never before. */
  void record(Design design, ActivityAction action, String detail);

  /** Paged trail for one Design, newest first. */
  ApiResponse<?> list(Long designId, GenericListRequestDTO request);
}
