package com.netlink.onemep_feature.design.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.design.dto.DesignTaskDto;
import com.netlink.onemep_feature.design.service.DesignTaskService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task section of the Design Detail screen (ONEMEP-38).
 *
 * <p>Status and Source have no routes here — both are read-only on this screen by the ticket's
 * rules. Status belongs to the approval flow; Source is fixed at creation.
 */
@RestController
@RequestMapping("/designs/{designId}")
@RequiredArgsConstructor
public class DesignTaskController {

  private final DesignTaskService designTaskService;

  @Operation(
      summary = "Fetch a Design's task details",
      tags = {"Designs"})
  @GetMapping("/task")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long designId) {
    return ResponseEntity.ok(designTaskService.get(designId));
  }

  /** Partial update — only the fields present in the body are applied. */
  @Operation(
      summary = "Update a Design's task details",
      tags = {"Designs"})
  @PatchMapping("/task")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long designId,
      @Valid @RequestBody DesignTaskDto.UpdateRequest request) {
    return ResponseEntity.ok(designTaskService.update(designId, request));
  }

  @Operation(
      summary = "Add a Tag to a Design",
      tags = {"Designs"})
  @PostMapping("/tags")
  public ResponseEntity<ApiResponse<?>> addTag(
      @PathVariable @NotNull Long designId,
      @Valid @RequestBody DesignTaskDto.AddTagRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(designTaskService.addTag(designId, request));
  }

  @Operation(
      summary = "Remove a Tag from a Design",
      tags = {"Designs"})
  @DeleteMapping("/tags/{tagId}")
  public ResponseEntity<ApiResponse<?>> removeTag(
      @PathVariable @NotNull Long designId, @PathVariable @NotNull Long tagId) {
    return ResponseEntity.ok(designTaskService.removeTag(designId, tagId));
  }
}
