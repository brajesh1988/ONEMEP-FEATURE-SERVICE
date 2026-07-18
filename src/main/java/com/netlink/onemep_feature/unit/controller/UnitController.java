package com.netlink.onemep_feature.unit.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.unit.dto.UnitDto;
import com.netlink.onemep_feature.unit.service.UnitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/units")
@RequiredArgsConstructor
public class UnitController {

  private final UnitService unitService;

  /** Unit listing with search + pagination (ONEMEP-26). */
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(unitService.list(request));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<?>> listActive() {
    return ResponseEntity.ok(unitService.listActive());
  }

  /** Add Unit (ONEMEP-27). */
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody UnitDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(unitService.get(id));
  }

  /** Edit Unit (ONEMEP-28). */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody UnitDto.UpdateRequest request) {
    return ResponseEntity.ok(unitService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(unitService.updateStatus(id, active));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(unitService.delete(id));
  }
}
