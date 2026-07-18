package com.netlink.onemep_feature.category.controller;

import com.netlink.onemep_feature.category.dto.CategoryDto;
import com.netlink.onemep_feature.category.service.CategoryService;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  /** Category listing with search + pagination (ONEMEP-22). */
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(categoryService.list(request));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<?>> listActive() {
    return ResponseEntity.ok(categoryService.listActive());
  }

  /** Add Category with a unique, locked prefix (ONEMEP-23). */
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody CategoryDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(categoryService.get(id));
  }

  /** Edit Category — prefix stays locked (ONEMEP-24). */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody CategoryDto.UpdateRequest request) {
    return ResponseEntity.ok(categoryService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(categoryService.updateStatus(id, active));
  }
}
