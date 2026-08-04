package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.service.ProjectDidSpecificationService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DID tab of the Technical Master: Design Intent & Brief, Delivery Schedule, Client Information,
 * Architect Team, Structure Consultant Team.
 *
 * <p>Read-only — saving happens only through the combined {@code PUT
 * /projects/{projectId}/technical-master} (ONEMEP-31). There is deliberately no DID-specific save
 * endpoint here.
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

  @GetMapping("/green-rating-options")
  public ResponseEntity<ApiResponse<?>> greenRatingOptions(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(didSpecificationService.listGreenRatingOptions());
  }
}
