package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto;

/**
 * DID tab (Technical Master → DID): Design Intent & Brief, Delivery Schedule, Client Information,
 * Architect Team, Structure Consultant Team.
 */
public interface ProjectDidSpecificationService {

  /** Consolidated read; returns an {@code exists:false} shell when none saved yet. */
  ApiResponse<?> get(Long projectId);

  /** Create-or-replace all five subsections in one transaction. */
  ApiResponse<?> upsert(Long projectId, DidSpecificationDto.UpsertRequest request);

  /** Configured options for the Green rating target dropdown. */
  ApiResponse<?> listGreenRatingOptions();
}
