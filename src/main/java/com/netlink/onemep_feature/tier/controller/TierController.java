package com.netlink.onemep_feature.tier.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.tier.dto.TierDto;
import com.netlink.onemep_feature.tier.service.TierService;
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
@RequestMapping("/tiers")
@RequiredArgsConstructor
public class TierController {

  private final TierService tierService;

  /** Tier listing with search + pagination (ONEMEP-16). */
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(tierService.list(request));
  }

  /** Active tiers for the role tier picker. */
  @GetMapping("/active")
  public ResponseEntity<ApiResponse<?>> listActive() {
    return ResponseEntity.ok(tierService.listActive());
  }

  /** Add Tier (ONEMEP-17). */
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody TierDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(tierService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(tierService.get(id));
  }

  /** Edit Tier (ONEMEP-18). */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody TierDto.UpdateRequest request) {
    return ResponseEntity.ok(tierService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(tierService.updateStatus(id, active));
  }
}
