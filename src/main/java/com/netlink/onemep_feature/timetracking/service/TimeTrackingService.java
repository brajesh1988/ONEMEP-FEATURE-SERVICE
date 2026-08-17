package com.netlink.onemep_feature.timetracking.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.timetracking.dto.TimeTrackingDto;

/** Design time tracking (ONEMEP-42). */
public interface TimeTrackingService {

  /** Everything the Time Tracking section shows, grouped by user and work date. */
  ApiResponse<?> summary(Long designId);

  ApiResponse<?> log(Long designId, TimeTrackingDto.LogRequest request);

  /** Deletes one entry. A user may only remove their own. */
  ApiResponse<?> delete(Long designId, Long entryId);
}
