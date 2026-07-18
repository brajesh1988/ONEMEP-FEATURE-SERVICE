package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;

public interface ProjectDeliveryScheduleService {

  ApiResponse<?> list(Long projectId);

  ApiResponse<?> create(Long projectId, DeliveryScheduleDto.CreateRequest request);

  ApiResponse<?> update(Long projectId, Long itemId, DeliveryScheduleDto.UpdateRequest request);

  ApiResponse<?> delete(Long projectId, Long itemId);
}
