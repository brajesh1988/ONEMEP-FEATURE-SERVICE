package com.netlink.onemep_feature.category.service;

import com.netlink.onemep_feature.category.dto.CategoryDto;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;

public interface CategoryService {
  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> listActive();

  ApiResponse<?> create(CategoryDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, CategoryDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);
}
