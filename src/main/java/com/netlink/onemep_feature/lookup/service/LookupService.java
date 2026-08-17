package com.netlink.onemep_feature.lookup.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.lookup.dto.LookupDto;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import java.util.List;

/**
 * Reference-data catalogue.
 *
 * <p>The {@code ApiResponse} methods back the HTTP surface. The {@code require*} methods are for
 * other features to resolve a submitted id against the catalogue with the type guard applied — call
 * those rather than loading {@link LookupValue} through a repository directly.
 */
public interface LookupService {

  ApiResponse<?> listOptions(LookupType type);

  ApiResponse<?> list(LookupType type, GenericListRequestDTO request);

  ApiResponse<?> create(LookupType type, LookupDto.CreateRequest request);

  ApiResponse<?> get(Long id);

  ApiResponse<?> update(Long id, LookupDto.UpdateRequest request);

  ApiResponse<?> updateStatus(Long id, Boolean active);

  /**
   * Resolves one id, rejecting it if it belongs to another catalogue or is inactive. Inactive is
   * rejected because every Sprint 3 story requires it — "One or more selected values are no longer
   * available."
   */
  LookupValue requireActive(LookupType type, Long id);

  /** Batch form of {@link #requireActive}; reports every offending id in one message. */
  List<LookupValue> requireAllActive(LookupType type, List<Long> ids);

  /** Resolves by code, used when importing spreadsheets where only codes are present. */
  LookupValue requireActiveByCode(LookupType type, String code);
}
