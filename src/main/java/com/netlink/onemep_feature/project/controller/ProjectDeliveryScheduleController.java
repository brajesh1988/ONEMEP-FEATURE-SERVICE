package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;
import com.netlink.onemep_feature.project.service.ProjectDeliveryScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Delivery schedule for a project (ONEMEP-15). */
@RestController
@RequestMapping("/projects/{projectId}/delivery-schedule")
@RequiredArgsConstructor
public class ProjectDeliveryScheduleController {

  private final ProjectDeliveryScheduleService deliveryScheduleService;

  @GetMapping
  public ResponseEntity<ApiResponse<?>> list(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(deliveryScheduleService.list(projectId));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @PathVariable @NotNull Long projectId,
      @Valid @RequestBody DeliveryScheduleDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(deliveryScheduleService.create(projectId, request));
  }

  @PutMapping("/{itemId}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long projectId,
      @PathVariable @NotNull Long itemId,
      @Valid @RequestBody DeliveryScheduleDto.UpdateRequest request) {
    return ResponseEntity.ok(deliveryScheduleService.update(projectId, itemId, request));
  }

  @DeleteMapping("/{itemId}")
  public ResponseEntity<ApiResponse<?>> delete(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long itemId) {
    return ResponseEntity.ok(deliveryScheduleService.delete(projectId, itemId));
  }
}
