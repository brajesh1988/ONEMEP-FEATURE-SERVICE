package com.netlink.onemep_feature.category.service;

import com.netlink.onemep_feature.category.dto.CategoryDto;
import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.category.repo.CategoryRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

  private static final Set<String> SORTABLE =
      Set.of("name", "categoryNumber", "prefix", "active", "createdDate", "updatedDate");

  private final CategoryRepo categoryRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<CategoryMaster> page =
        categoryRepo.findAll(searchSpec(search), PageableFactory.of(request, SORTABLE));
    List<CategoryDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
    return apiResponseAdaptor.success(
        "Categories fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listActive() {
    List<CategoryDto.ActiveItem> items =
        categoryRepo.findAllActive().stream()
            .map(c -> new CategoryDto.ActiveItem(c.getId(), c.getName(), c.getPrefix()))
            .toList();
    return apiResponseAdaptor.success("Active categories fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(CategoryDto.CreateRequest request) {
    String name = normalize(request.name());
    String prefix = request.prefix() == null ? "" : request.prefix().trim().toUpperCase();

    categoryRepo
        .findByNameIgnoreCase(name)
        .ifPresent(
            c -> {
              throw new DuplicateResourceException("A category with this name already exists.");
            });
    categoryRepo
        .findByPrefixIgnoreCase(prefix)
        .ifPresent(
            c -> {
              throw new DuplicateResourceException("A category with this prefix already exists.");
            });

    CategoryMaster category = new CategoryMaster();
    category.setName(name);
    category.setPrefix(prefix);
    category.setActive(request.active() == null ? Boolean.TRUE : request.active());
    category.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    // A temp, unique placeholder satisfies NOT NULL/UNIQUE on the initial insert; the real,
    // id-derived number is written on the follow-up update once the id is generated.
    category.setCategoryNumber("TMP-" + UUID.randomUUID());
    category = categoryRepo.saveAndFlush(category);
    category.setCategoryNumber(String.format("CAT-%05d", category.getId()));
    category = categoryRepo.save(category);
    log.info("Created categoryId={} number={}", category.getId(), category.getCategoryNumber());
    return apiResponseAdaptor.success("Category created successfully.", toResponse(category));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Category fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, CategoryDto.UpdateRequest request) {
    CategoryMaster category = require(id);
    String name = normalize(request.name());
    categoryRepo
        .findByNameIgnoreCaseAndIdNot(name, id)
        .ifPresent(
            c -> {
              throw new DuplicateResourceException("A category with this name already exists.");
            });
    // category_number and prefix are intentionally NOT updated — they are locked.
    category.setName(name);
    if (request.active() != null) {
      category.setActive(request.active());
    }
    category.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    categoryRepo.save(category);
    log.info("Updated categoryId={}", id);
    return apiResponseAdaptor.success("Category updated successfully.", toResponse(category));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    CategoryMaster category = require(id);
    category.setActive(active);
    category.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    categoryRepo.save(category);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Category activated successfully."
            : "Category deactivated successfully.");
  }

  private CategoryMaster require(Long id) {
    return categoryRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private Specification<CategoryMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      String like = "%" + search.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("name")), like),
          cb.like(cb.lower(root.get("prefix")), like),
          cb.like(cb.lower(root.get("categoryNumber")), like));
    };
  }

  private CategoryDto.Response toResponse(CategoryMaster c) {
    return new CategoryDto.Response(
        c.getId(),
        c.getCategoryNumber(),
        c.getName(),
        c.getPrefix(),
        c.getActive(),
        c.getUpdatedBy(),
        c.getUpdatedDate());
  }
}
