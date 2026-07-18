package com.netlink.onemep_feature.detailinglevel.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.detailinglevel.dto.DetailingLevelDto;

public interface DetailingLevelService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(DetailingLevelDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, DetailingLevelDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);
}
