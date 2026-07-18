package com.netlink.onemep_feature.detailinglevel.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.detailinglevel.dto.DetailingLevelDto;
import com.netlink.onemep_feature.detailinglevel.service.DetailingLevelService;
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
@RequestMapping("/detailing-levels")
@RequiredArgsConstructor
public class DetailingLevelController {

  private final DetailingLevelService detailingLevelService;

  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(detailingLevelService.list(request));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<?>> listActive() {
    return ResponseEntity.ok(detailingLevelService.listActive());
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody DetailingLevelDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(detailingLevelService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(detailingLevelService.get(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody DetailingLevelDto.UpdateRequest request) {
    return ResponseEntity.ok(detailingLevelService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(detailingLevelService.updateStatus(id, active));
  }
}
