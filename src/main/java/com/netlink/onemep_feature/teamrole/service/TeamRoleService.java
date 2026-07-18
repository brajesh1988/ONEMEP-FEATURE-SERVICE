package com.netlink.onemep_feature.teamrole.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.teamrole.dto.TeamRoleDto;

public interface TeamRoleService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(TeamRoleDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, TeamRoleDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);

  ApiResponse<?> delete(Long id);
}
