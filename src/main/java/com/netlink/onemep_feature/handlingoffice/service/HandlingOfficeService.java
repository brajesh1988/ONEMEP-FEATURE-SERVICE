package com.netlink.onemep_feature.handlingoffice.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.handlingoffice.dto.HandlingOfficeDto;

public interface HandlingOfficeService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(HandlingOfficeDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, HandlingOfficeDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);
}
