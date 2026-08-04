package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;

/**
 * DID tab (Technical Master → DID): Design Intent & Brief, Delivery Schedule, Client Information,
 * Architect Team, Structure Consultant Team.
 *
 * <p>Saving is only reachable through the combined Technical Master save (ONEMEP-31) — there is no
 * standalone DID save endpoint, by design.
 */
public interface ProjectDidSpecificationService {

  /** Consolidated read; returns an {@code exists:false} shell when none saved yet. */
  ApiResponse<?> get(Long projectId);

  /** Same read as {@link #get}, unwrapped — for internal callers (e.g. XLSX export). */
  DidSpecificationDto.Response getResponseData(Long projectId);

  /**
   * Create-or-replace all five subsections against an already-resolved project/master, as part of
   * the caller's own transaction (joins it — propagation {@code REQUIRED} — so a DID validation
   * failure rolls back the caller's Technical Master changes too).
   */
  DidSpecificationDto.Response applyUpsert(
      ProjectMaster project,
      ProjectTechnicalMaster master,
      DidSpecificationDto.UpsertRequest request,
      Long currentUser);

  /** Configured options for the Green rating target dropdown. */
  ApiResponse<?> listGreenRatingOptions();
}
