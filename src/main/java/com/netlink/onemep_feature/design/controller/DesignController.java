package com.netlink.onemep_feature.design.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.design.dto.DesignDto;
import com.netlink.onemep_feature.design.service.DesignService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Design Register (ONEMEP-35/36/37).
 *
 * <p>Collection routes are nested under the Project because a Design only ever exists inside one
 * and the register must never show another Project's Designs. Single-Design routes are flat, since
 * ids are unique service-wide.
 */
@RestController
@RequiredArgsConstructor
public class DesignController {

  private final DesignService designService;

  /** Design Register listing with search, filters and pagination (ONEMEP-35). */
  @Operation(
      summary = "List a Project's Designs with search, filters and pagination",
      tags = {"Designs"})
  @PostMapping("/projects/{projectId}/designs/list")
  public ResponseEntity<ApiResponse<?>> list(
      @PathVariable @NotNull Long projectId, @Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(designService.list(projectId, request));
  }

  /** Add Design (ONEMEP-36). The Design Number is generated, never supplied. */
  @Operation(
      summary = "Create a Design in a Project",
      tags = {"Designs"})
  @PostMapping("/projects/{projectId}/designs")
  public ResponseEntity<ApiResponse<?>> create(
      @PathVariable @NotNull Long projectId, @Valid @RequestBody DesignDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(designService.create(projectId, request));
  }

  /** Live Design Number preview while the Add form is being filled in. */
  @Operation(
      summary = "Preview the Design Number a segment selection would produce",
      tags = {"Designs"})
  @PostMapping("/projects/{projectId}/designs/number-preview")
  public ResponseEntity<ApiResponse<?>> previewNumber(
      @PathVariable @NotNull Long projectId, @Valid @RequestBody DesignDto.CreateRequest request) {
    return ResponseEntity.ok(designService.previewNumber(projectId, request));
  }

  @Operation(
      summary = "Fetch a Design",
      tags = {"Designs"})
  @GetMapping("/designs/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(designService.get(id));
  }

  /** Edit Design (ONEMEP-37). Segments and the Design Number are not editable. */
  @Operation(
      summary = "Edit a Design's descriptive information",
      tags = {"Designs"})
  @PutMapping("/designs/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody DesignDto.UpdateRequest request) {
    return ResponseEntity.ok(designService.update(id, request));
  }

  @Operation(
      summary = "Delete a Design",
      tags = {"Designs"})
  @DeleteMapping("/designs/{id}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(designService.delete(id));
  }
}
