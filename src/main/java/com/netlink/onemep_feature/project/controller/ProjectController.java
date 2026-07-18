package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.project.dto.ProjectDto;
import com.netlink.onemep_feature.project.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

  private final ProjectService projectService;

  /** Projects Listing with search + filter + pagination (ONEMEP-12). */
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(projectService.list(request));
  }

  /** Add Project (ONEMEP-13). */
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody ProjectDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(projectService.get(id));
  }

  /** Project Overview (ONEMEP-15). */
  @GetMapping("/{id}/overview")
  public ResponseEntity<ApiResponse<?>> overview(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(projectService.overview(id));
  }

  /** Edit Project — project number and category stay protected (ONEMEP-14). */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody ProjectDto.UpdateRequest request) {
    return ResponseEntity.ok(projectService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(projectService.updateStatus(id, active));
  }

  /**
   * Change only the lifecycle status (ONEMEP-12/15). A reason is mandatory when moving to On Hold
   * or Closed; a notification is sent to the project leads.
   */
  @PatchMapping("/{id}/lifecycle")
  public ResponseEntity<ApiResponse<?>> updateLifecycle(
      @PathVariable @NotNull Long id,
      @RequestParam @NotBlank String lifecycleStatus,
      @RequestParam(required = false) String reason) {
    return ResponseEntity.ok(projectService.updateLifecycle(id, lifecycleStatus, reason));
  }

  /** Change only the project priority; triggers a notification to the project leads. */
  @PatchMapping("/{id}/priority")
  public ResponseEntity<ApiResponse<?>> updatePriority(
      @PathVariable @NotNull Long id, @RequestParam @NotBlank String priority) {
    return ResponseEntity.ok(projectService.updatePriority(id, priority));
  }

  /**
   * Confirm a project from the listing (ONEMEP-12): Non-confirmed → Confirmed reassigns the Project
   * ID from the category series and locks the type. The transition is irreversible.
   */
  @PatchMapping("/{id}/type")
  public ResponseEntity<ApiResponse<?>> updateType(
      @PathVariable @NotNull Long id, @RequestParam @NotBlank String type) {
    return ResponseEntity.ok(projectService.updateType(id, type));
  }
}
