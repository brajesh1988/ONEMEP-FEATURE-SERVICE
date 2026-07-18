package com.netlink.onemep_feature.detailinglevel.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.detailinglevel.dto.DetailingLevelDto;
import com.netlink.onemep_feature.detailinglevel.model.DetailingLevelMaster;
import com.netlink.onemep_feature.detailinglevel.repo.DetailingLevelRepo;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
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
public class DetailingLevelServiceImpl implements DetailingLevelService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final DetailingLevelRepo repo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<DetailingLevelMaster> page =
        repo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<DetailingLevelDto.Response> content =
        page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Detailing levels fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<DetailingLevelDto.ActiveItem> items =
        repo.findAllActive().stream()
            .map(d -> new DetailingLevelDto.ActiveItem(d.getId(), d.getName()))
            .toList();
    return apiResponseAdaptor.success("Active detailing levels fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(DetailingLevelDto.CreateRequest request) {
    String name = normalize(request.name());
    repo.findByNameIgnoreCase(name)
        .ifPresent(
            d -> {
              throw new DuplicateResourceException(
                  "A detailing level with this name already exists.");
            });
    DetailingLevelMaster level = new DetailingLevelMaster();
    level.setName(name);
    level.setActive(request.active() == null ? Boolean.TRUE : request.active());
    level.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    level = repo.save(level);
    log.info("Created detailingLevelId={} name={}", level.getId(), name);
    return apiResponseAdaptor.success("Detailing level created successfully.", toResponse(level));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success(
        "Detailing level fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, DetailingLevelDto.UpdateRequest request) {
    DetailingLevelMaster level = require(id);
    String name = normalize(request.name());
    repo.findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            d -> {
              throw new DuplicateResourceException(
                  "A detailing level with this name already exists.");
            });
    level.setName(name);
    if (request.active() != null) {
      level.setActive(request.active());
    }
    level.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    repo.save(level);
    log.info("Updated detailingLevelId={}", id);
    return apiResponseAdaptor.success("Detailing level updated successfully.", toResponse(level));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    DetailingLevelMaster level = require(id);
    level.setActive(active);
    level.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    repo.save(level);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Detailing level activated successfully."
            : "Detailing level deactivated successfully.");
  }

  private DetailingLevelMaster require(Long id) {
    return repo.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Detailing level not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<DetailingLevelMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    };
  }

  private DetailingLevelDto.Response toResponse(DetailingLevelMaster d) {
    return new DetailingLevelDto.Response(
        d.getId(), d.getName(), d.getActive(), d.getUpdatedBy(), d.getUpdatedDate());
  }
}
