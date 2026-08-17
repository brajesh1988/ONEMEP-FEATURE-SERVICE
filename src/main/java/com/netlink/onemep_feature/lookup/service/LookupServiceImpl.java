package com.netlink.onemep_feature.lookup.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.dto.LookupDto;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.repo.LookupValueRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupServiceImpl implements LookupService {

  private static final Set<String> SORTABLE =
      Set.of("code", "label", "sortOrder", "active", "createdDate", "updatedDate");

  private final LookupValueRepo lookupValueRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listOptions(LookupType type) {
    List<LookupDto.Option> options =
        lookupValueRepo.findActiveByType(type).stream()
            .map(l -> new LookupDto.Option(l.getId(), l.getCode(), l.getLabel()))
            .toList();
    return apiResponseAdaptor.success(label(type) + " options fetched successfully.", options);
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(LookupType type, GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<LookupValue> page =
        lookupValueRepo.findAll(searchSpec(type, search), PageableFactory.of(request, SORTABLE));
    List<LookupDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        label(type) + " values fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> create(LookupType type, LookupDto.CreateRequest request) {
    String code = normalizeCode(request.code());
    lookupValueRepo
        .findByTypeAndCode(type, code)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(
                  "A "
                      + label(type).toLowerCase()
                      + " value with code '"
                      + code
                      + "' already"
                      + " exists.");
            });

    LookupValue value = new LookupValue();
    value.setLookupType(type);
    value.setCode(code);
    value.setLabel(request.label().trim());
    value.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    value.setActive(request.active() == null ? Boolean.TRUE : request.active());
    value.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    value = lookupValueRepo.save(value);

    log.info("Created lookupId={} type={} code={}", value.getId(), type, code);
    return apiResponseAdaptor.success(
        label(type) + " value created successfully.", toResponse(value));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success(
        "Lookup value fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, LookupDto.UpdateRequest request) {
    LookupValue value = require(id);
    String code = normalizeCode(request.code());
    lookupValueRepo
        .findByTypeAndCodeExcluding(value.getLookupType(), code, id)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(
                  "A "
                      + label(value.getLookupType()).toLowerCase()
                      + " value with code '"
                      + code
                      + "' already exists.");
            });

    // lookupType is intentionally not settable: a value's catalogue is fixed at creation, and
    // consumers hold composite foreign keys that pin it.
    value.setCode(code);
    value.setLabel(request.label().trim());
    if (request.sortOrder() != null) {
      value.setSortOrder(request.sortOrder());
    }
    if (request.active() != null) {
      value.setActive(request.active());
    }
    value.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    lookupValueRepo.save(value);

    log.info("Updated lookupId={} code={}", id, code);
    return apiResponseAdaptor.success(
        label(value.getLookupType()) + " value updated successfully.", toResponse(value));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    LookupValue value = require(id);
    value.setActive(active);
    value.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    lookupValueRepo.save(value);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? label(value.getLookupType()) + " value activated successfully."
            : label(value.getLookupType()) + " value deactivated successfully.");
  }

  @Override
  @Transactional(readOnly = true)
  public LookupValue requireActive(LookupType type, Long id) {
    LookupValue value =
        lookupValueRepo
            .findById(id)
            .filter(l -> l.getLookupType() == type)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "The selected " + label(type).toLowerCase() + " is not valid."));
    if (!Boolean.TRUE.equals(value.getActive())) {
      throw new ResourceNotFoundException(
          "One or more selected values are no longer available. Update your selections and try"
              + " again.");
    }
    return value;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LookupValue> requireAllActive(LookupType type, List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<Long> distinct = ids.stream().distinct().toList();
    Map<Long, LookupValue> found =
        lookupValueRepo.findAllByTypeAndIdIn(type, distinct).stream()
            .collect(Collectors.toMap(LookupValue::getId, Function.identity()));

    List<Long> invalid =
        distinct.stream()
            .filter(id -> !found.containsKey(id) || !Boolean.TRUE.equals(found.get(id).getActive()))
            .toList();
    if (!invalid.isEmpty()) {
      throw new ResourceNotFoundException(
          "One or more selected values are no longer available. Update your selections and try"
              + " again.");
    }
    return distinct.stream().map(found::get).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public LookupValue requireActiveByCode(LookupType type, String code) {
    LookupValue value =
        lookupValueRepo
            .findByTypeAndCode(type, normalizeCode(code))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        label(type) + " '" + code + "' is not configured."));
    if (!Boolean.TRUE.equals(value.getActive())) {
      throw new ResourceNotFoundException(label(type) + " '" + code + "' is no longer available.");
    }
    return value;
  }

  private LookupValue require(Long id) {
    return lookupValueRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Lookup value not found."));
  }

  /** Codes are matched case-insensitively everywhere; store them upper-case for consistency. */
  private static String normalizeCode(String raw) {
    return raw == null ? "" : raw.trim().toUpperCase();
  }

  private static String label(LookupType type) {
    return switch (type) {
      case DISCIPLINE -> "Discipline";
      case DESIGN_TYPE -> "Type";
      case SUBJECT -> "Subject";
      case FLOOR -> "Floor";
      case ZONE -> "Zone";
      case STAGE -> "Stage";
    };
  }

  private Specification<LookupValue> searchSpec(LookupType type, String search) {
    return (root, query, cb) -> {
      var typeMatches = cb.equal(root.get("lookupType"), type);
      if (search == null) {
        return typeMatches;
      }
      String term = "%" + search.toLowerCase() + "%";
      return cb.and(
          typeMatches,
          cb.or(
              cb.like(cb.lower(root.get("code")), term),
              cb.like(cb.lower(root.get("label")), term)));
    };
  }

  private LookupDto.Response toResponse(LookupValue l) {
    return new LookupDto.Response(
        l.getId(),
        l.getLookupType().name(),
        l.getCode(),
        l.getLabel(),
        l.getSortOrder(),
        l.getActive(),
        l.getUpdatedBy(),
        l.getUpdatedDate());
  }
}
