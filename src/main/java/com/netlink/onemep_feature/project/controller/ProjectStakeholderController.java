package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.StakeholderDto;
import com.netlink.onemep_feature.project.service.ProjectStakeholderService;
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

/** Stakeholder directory for a project (ONEMEP-15): project head / architect / structure, etc. */
@RestController
@RequestMapping("/projects/{projectId}/stakeholders")
@RequiredArgsConstructor
public class ProjectStakeholderController {

  private final ProjectStakeholderService stakeholderService;

  @GetMapping
  public ResponseEntity<ApiResponse<?>> list(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(stakeholderService.list(projectId));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @PathVariable @NotNull Long projectId,
      @Valid @RequestBody StakeholderDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stakeholderService.create(projectId, request));
  }

  @PutMapping("/{stakeholderId}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long projectId,
      @PathVariable @NotNull Long stakeholderId,
      @Valid @RequestBody StakeholderDto.UpdateRequest request) {
    return ResponseEntity.ok(stakeholderService.update(projectId, stakeholderId, request));
  }

  @DeleteMapping("/{stakeholderId}")
  public ResponseEntity<ApiResponse<?>> delete(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long stakeholderId) {
    return ResponseEntity.ok(stakeholderService.delete(projectId, stakeholderId));
  }
}
