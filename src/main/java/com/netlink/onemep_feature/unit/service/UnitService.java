package com.netlink.onemep_feature.unit.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.unit.dto.UnitDto;

public interface UnitService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(UnitDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, UnitDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);

  ApiResponse<?> delete(Long id);
}
