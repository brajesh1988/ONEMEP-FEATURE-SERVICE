package com.netlink.onemep_feature.unit.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceInUseException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.dto.UnitDto;
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
public class UnitServiceImpl implements UnitService {

  private static final Set<String> SORTABLE =
      Set.of("name", "symbol", "acceptedInputType", "active", "createdDate", "updatedDate");
  private static final Set<String> INPUT_TYPES = Set.of("INTEGER", "DECIMAL", "TEXT", "BOOLEAN");

  private final UnitRepo unitRepo;
  private final TechnicalMasterRepo technicalMasterRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<UnitMaster> page =
        unitRepo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<UnitDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Units fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<UnitDto.ActiveItem> items =
        unitRepo.findAllActive().stream()
            .map(
                u ->
                    new UnitDto.ActiveItem(
                        u.getId(), u.getName(), u.getSymbol(), u.getAcceptedInputType()))
            .toList();
    return apiResponseAdaptor.success("Active units fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(UnitDto.CreateRequest request) {
    String name = normalize(request.name());
    String symbol = normalize(request.symbol());
    String inputType = validateInputType(request.acceptedInputType());
    unitRepo
        .findBySymbolIgnoreCase(symbol)
        .ifPresent(
            u -> {
              throw new DuplicateResourceException("A unit with this symbol already exists.");
            });

    UnitMaster unit = new UnitMaster();
    unit.setName(name);
    unit.setSymbol(symbol);
    unit.setAcceptedInputType(inputType);
    unit.setActive(request.active() == null ? Boolean.TRUE : request.active());
    unit.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    unit = unitRepo.save(unit);
    log.info("Created unitId={} symbol={}", unit.getId(), symbol);
    return apiResponseAdaptor.success("Unit created successfully.", toResponse(unit));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Unit fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, UnitDto.UpdateRequest request) {
    UnitMaster unit = require(id);
    String name = normalize(request.name());
    String symbol = normalize(request.symbol());
    String inputType = validateInputType(request.acceptedInputType());
    unitRepo
        .findBySymbolIgnoreCaseAndIdNot(symbol, id)
        .ifPresent(
            u -> {
              throw new DuplicateResourceException("A unit with this symbol already exists.");
            });
    unit.setName(name);
    unit.setSymbol(symbol);
    unit.setAcceptedInputType(inputType);
    if (request.active() != null) {
      unit.setActive(request.active());
    }
    unit.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    unitRepo.save(unit);
    log.info("Updated unitId={}", id);
    return apiResponseAdaptor.success("Unit updated successfully.", toResponse(unit));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    UnitMaster unit = require(id);
    unit.setActive(active);
    unit.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    unitRepo.save(unit);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Unit activated successfully."
            : "Unit deactivated successfully.");
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long id) {
    UnitMaster unit = require(id);
    if (technicalMasterRepo.countByUnit_Id(id) > 0) {
      throw new ResourceInUseException(
          "This unit is referenced by Technical Master data and cannot be deleted.");
    }
    unitRepo.delete(unit);
    log.info("Deleted unitId={}", id);
    return apiResponseAdaptor.success("Unit deleted successfully.");
  }

  private UnitMaster require(Long id) {
    return unitRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Unit not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private static String validateInputType(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase();
    if (!INPUT_TYPES.contains(value)) {
      throw new ApplicationException(
          "Accepted input type must be one of: INTEGER, DECIMAL, TEXT, BOOLEAN.");
    }
    return value;
  }

  private Specification<UnitMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      String like = "%" + search.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("symbol")), like));
    };
  }

  private UnitDto.Response toResponse(UnitMaster u) {
    return new UnitDto.Response(
        u.getId(),
        u.getName(),
        u.getSymbol(),
        u.getAcceptedInputType(),
        u.getActive(),
        u.getUpdatedBy(),
        u.getUpdatedDate());
  }
}
