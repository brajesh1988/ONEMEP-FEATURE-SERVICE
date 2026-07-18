package com.netlink.onemep_feature.tier.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.tier.dto.TierDto;

public interface TierService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(TierDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, TierDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);
}
