package com.netlink.onemep_feature.handlingoffice.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.handlingoffice.dto.HandlingOfficeDto;
import com.netlink.onemep_feature.handlingoffice.model.HandlingOfficeMaster;
import com.netlink.onemep_feature.handlingoffice.repo.HandlingOfficeRepo;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HandlingOfficeServiceImpl implements HandlingOfficeService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final HandlingOfficeRepo repo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<HandlingOfficeMaster> page =
        repo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<HandlingOfficeDto.Response> content =
        page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Handling offices fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<HandlingOfficeDto.ActiveItem> items =
        repo.findAllActive().stream()
            .map(h -> new HandlingOfficeDto.ActiveItem(h.getId(), h.getName()))
            .toList();
    return apiResponseAdaptor.success("Active handling offices fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(HandlingOfficeDto.CreateRequest request) {
    String name = normalize(request.name());
    repo.findByNameIgnoreCase(name)
        .ifPresent(
            h -> {
              throw new DuplicateResourceException(
                  "A handling office with this name already exists.");
            });
    HandlingOfficeMaster office = new HandlingOfficeMaster();
    office.setName(name);
    office.setActive(request.active() == null ? Boolean.TRUE : request.active());
    office.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    office = repo.save(office);
    log.info("Created handlingOfficeId={} name={}", office.getId(), name);
    return apiResponseAdaptor.success("Handling office created successfully.", toResponse(office));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success(
        "Handling office fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, HandlingOfficeDto.UpdateRequest request) {
    HandlingOfficeMaster office = require(id);
    String name = normalize(request.name());
    repo.findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            h -> {
              throw new DuplicateResourceException(
                  "A handling office with this name already exists.");
            });
    office.setName(name);
    if (request.active() != null) {
      office.setActive(request.active());
    }
    office.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    repo.save(office);
    log.info("Updated handlingOfficeId={}", id);
    return apiResponseAdaptor.success("Handling office updated successfully.", toResponse(office));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    HandlingOfficeMaster office = require(id);
    office.setActive(active);
    office.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    repo.save(office);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Handling office activated successfully."
            : "Handling office deactivated successfully.");
  }

  private HandlingOfficeMaster require(Long id) {
    return repo.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Handling office not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<HandlingOfficeMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    };
  }

  private HandlingOfficeDto.Response toResponse(HandlingOfficeMaster h) {
    return new HandlingOfficeDto.Response(
        h.getId(), h.getName(), h.getActive(), h.getUpdatedBy(), h.getUpdatedDate());
  }
}
