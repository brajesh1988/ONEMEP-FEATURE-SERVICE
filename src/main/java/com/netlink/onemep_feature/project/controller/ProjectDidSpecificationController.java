package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto;
import com.netlink.onemep_feature.project.service.ProjectDidSpecificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DID tab of the Technical Master: Design Intent & Brief, Delivery Schedule, Client Information,
 * Architect Team, Structure Consultant Team.
 */
@RestController
@RequestMapping("/projects/{projectId}/technical-master/did")
@RequiredArgsConstructor
public class ProjectDidSpecificationController {

  private final ProjectDidSpecificationService didSpecificationService;

  /** Consolidated read; returns an {@code exists:false} shell when none saved yet. */
  @GetMapping
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(didSpecificationService.get(projectId));
  }

  /** Create-or-replace all five DID subsections in one transaction. */
  @PutMapping
  public ResponseEntity<ApiResponse<?>> upsert(
      @PathVariable @NotNull Long projectId,
      @Valid @RequestBody DidSpecificationDto.UpsertRequest request) {
    return ResponseEntity.ok(didSpecificationService.upsert(projectId, request));
  }

  @GetMapping("/green-rating-options")
  public ResponseEntity<ApiResponse<?>> greenRatingOptions(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(didSpecificationService.listGreenRatingOptions());
  }
}
