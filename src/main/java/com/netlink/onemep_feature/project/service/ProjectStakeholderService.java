package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.StakeholderDto;

public interface ProjectStakeholderService {

  ApiResponse<?> list(Long projectId);

  ApiResponse<?> create(Long projectId, StakeholderDto.CreateRequest request);

  ApiResponse<?> update(Long projectId, Long stakeholderId, StakeholderDto.UpdateRequest request);

  ApiResponse<?> delete(Long projectId, Long stakeholderId);
}
