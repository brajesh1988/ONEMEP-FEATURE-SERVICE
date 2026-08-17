package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.design.dto.DesignTaskDto;
import com.netlink.onemep_feature.design.model.Design;

/** Task section of the Design Detail screen (ONEMEP-38). */
public interface DesignTaskService {

  ApiResponse<?> get(Long designId);

  /**
   * Applies a partial update. ONEMEP-38 leaves the save model open (per-field auto-save versus one
   * form submit) while insisting it be consistent; this is the single-submit shape, so the whole
   * Task section commits or none of it does.
   */
  ApiResponse<?> update(Long designId, DesignTaskDto.UpdateRequest request);

  ApiResponse<?> addTag(Long designId, DesignTaskDto.AddTagRequest request);

  ApiResponse<?> removeTag(Long designId, Long tagId);

  /** Builds the Task view for an already-loaded Design, so the Detail screen needs one fetch. */
  DesignTaskDto.View toView(Design design);
}
