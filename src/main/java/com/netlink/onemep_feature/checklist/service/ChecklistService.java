package com.netlink.onemep_feature.checklist.service;

import com.netlink.onemep_feature.checklist.dto.ChecklistDto;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;

/** Checklist Master maintenance and applicability resolution (ONEMEP-32/33/34). */
public interface ChecklistService {

  ApiResponse<?> list(GenericListRequestDTO request);

  ApiResponse<?> create(ChecklistDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, ChecklistDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);

  /** Matching-Design count shown before deletion. */
  ApiResponse<?> impact(Long id);

  ApiResponse<?> delete(Long id);

  /**
   * Checklists applicable to a Design's Discipline/Type/Subject, following the OR-within-segment,
   * AND-across-segment rule. Consumed by the approval flow (ONEMEP-40).
   */
  ApiResponse<?> applicable(Long disciplineId, Long typeId, Long subjectId);
}
