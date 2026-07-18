package com.netlink.onemep_feature.technical.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.technical.dto.TechnicalDto;

public interface TechnicalService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> create(TechnicalDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, TechnicalDto.UpdateRequest request);

  ApiResponse<?> delete(Long id);
}
