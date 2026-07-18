package com.netlink.onemep_feature.tier.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.tier.dto.TierDto;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.tier.repo.TierRepo;
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
public class TierServiceImpl implements TierService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final TierRepo tierRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<TierMaster> page =
        tierRepo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<TierDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Tiers fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<TierDto.ActiveItem> items =
        tierRepo.findAllActive().stream()
            .map(t -> new TierDto.ActiveItem(t.getId(), t.getName()))
            .toList();
    return apiResponseAdaptor.success("Active tiers fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(TierDto.CreateRequest request) {
    String name = normalize(request.name());
    tierRepo
        .findByNameIgnoreCase(name)
        .ifPresent(
            t -> {
              throw new DuplicateResourceException("A tier with this name already exists.");
            });

    TierMaster tier = new TierMaster();
    tier.setName(name);
    tier.setActive(request.active() == null ? Boolean.TRUE : request.active());
    tier.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    tier = tierRepo.save(tier);
    log.info("Created tierId={} name={}", tier.getId(), name);
    return apiResponseAdaptor.success("Tier created successfully.", toResponse(tier));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Tier fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, TierDto.UpdateRequest request) {
    TierMaster tier = require(id);
    String name = normalize(request.name());
    tierRepo
        .findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            t -> {
              throw new DuplicateResourceException("A tier with this name already exists.");
            });
    tier.setName(name);
    if (request.active() != null) {
      tier.setActive(request.active());
    }
    tier.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    tierRepo.save(tier);
    log.info("Updated tierId={}", id);
    return apiResponseAdaptor.success("Tier updated successfully.", toResponse(tier));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    TierMaster tier = require(id);
    tier.setActive(active);
    tier.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    tierRepo.save(tier);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Tier activated successfully."
            : "Tier deactivated successfully.");
  }

  private TierMaster require(Long id) {
    return tierRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Tier not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<TierMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    };
  }

  private TierDto.Response toResponse(TierMaster t) {
    return new TierDto.Response(
        t.getId(), t.getName(), t.getActive(), t.getUpdatedBy(), t.getUpdatedDate());
  }
}
