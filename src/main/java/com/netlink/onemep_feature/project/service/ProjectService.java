package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.project.dto.ProjectDto;

public interface ProjectService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> create(ProjectDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> overview(Long id);

  ApiResponse<?> update(Long id, ProjectDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);

  ApiResponse<?> updateLifecycle(Long id, String lifecycleStatus);

  ApiResponse<?> updatePriority(Long id, String priority);
}
