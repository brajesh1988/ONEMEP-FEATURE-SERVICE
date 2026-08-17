package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.design.dto.DesignDto;

/** Design Register (ONEMEP-35 listing, ONEMEP-36 add, ONEMEP-37 edit). */
public interface DesignService {

  /**
   * Paged listing scoped to one Project. Filters ({@code disciplineId}, {@code stageId}, {@code
   * progress}) and the free-text search are read from the request's filter map and combined with
   * AND, against the whole dataset before pagination.
   */
  ApiResponse<?> list(Long projectId, GenericListRequestDTO request);

  ApiResponse<?> create(Long projectId, DesignDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  /** Updates the descriptive fields only; the Design Number configuration stays as created. */
  ApiResponse<?> update(Long id, DesignDto.UpdateRequest request);

  ApiResponse<?> delete(Long id);

  /** Live Design Number preview for the Add screen, without creating anything. */
  ApiResponse<?> previewNumber(Long projectId, DesignDto.CreateRequest request);
}
