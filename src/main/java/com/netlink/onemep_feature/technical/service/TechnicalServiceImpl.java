package com.netlink.onemep_feature.technical.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.technical.dto.TechnicalDto;
import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.model.UnitMaster;
import com.netlink.onemep_feature.unit.repo.UnitRepo;
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
public class TechnicalServiceImpl implements TechnicalService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final TechnicalMasterRepo technicalMasterRepo;
  private final UnitRepo unitRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<TechnicalMaster> page =
        technicalMasterRepo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<TechnicalDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Technical master fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> create(TechnicalDto.CreateRequest request) {
    String name = normalize(request.name());
    technicalMasterRepo
        .findByNameIgnoreCase(name)
        .ifPresent(
            t -> {
              throw new DuplicateResourceException(
                  "A technical field with this name already exists.");
            });
    TechnicalMaster technical = new TechnicalMaster();
    technical.setName(name);
    technical.setUnit(resolveUnit(request.unitId()));
    technical.setActive(request.active() == null ? Boolean.TRUE : request.active());
    technical.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    technical = technicalMasterRepo.save(technical);
    return apiResponseAdaptor.success(
        "Technical field created successfully.", toResponse(technical));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success(
        "Technical field fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, TechnicalDto.UpdateRequest request) {
    TechnicalMaster technical = require(id);
    String name = normalize(request.name());
    technicalMasterRepo
        .findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            t -> {
              throw new DuplicateResourceException(
                  "A technical field with this name already exists.");
            });
    technical.setName(name);
    technical.setUnit(resolveUnit(request.unitId()));
    if (request.active() != null) {
      technical.setActive(request.active());
    }
    technical.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    technicalMasterRepo.save(technical);
    return apiResponseAdaptor.success(
        "Technical field updated successfully.", toResponse(technical));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long id) {
    technicalMasterRepo.delete(require(id));
    return apiResponseAdaptor.success("Technical field deleted successfully.");
  }

  private TechnicalMaster require(Long id) {
    return technicalMasterRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Technical field not found."));
  }

  private UnitMaster resolveUnit(Long unitId) {
    if (unitId == null) {
      return null;
    }
    return unitRepo
        .findById(unitId)
        .orElseThrow(() -> new ResourceNotFoundException("Unit not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<TechnicalMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    };
  }

  private TechnicalDto.Response toResponse(TechnicalMaster t) {
    UnitMaster unit = t.getUnit();
    return new TechnicalDto.Response(
        t.getId(),
        t.getName(),
        unit == null ? null : unit.getId(),
        unit == null ? null : unit.getSymbol(),
        t.getActive(),
        t.getUpdatedBy(),
        t.getUpdatedDate());
  }
}
