package com.netlink.onemep_feature.checklist.controller;

import com.netlink.onemep_feature.checklist.dto.ChecklistDto;
import com.netlink.onemep_feature.checklist.service.ChecklistService;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Checklist Master (ONEMEP-32/33/34). */
@RestController
@RequestMapping("/checklists")
@RequiredArgsConstructor
public class ChecklistController {

  private final ChecklistService checklistService;

  /** Checklist listing with search + pagination (ONEMEP-32). */
  @Operation(
      summary = "List checklist master records with search and pagination",
      tags = {"Checklists"})
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(checklistService.list(request));
  }

  /** Add Checklist or Single Item (ONEMEP-33). */
  @Operation(
      summary = "Create a Checklist or Single Item",
      tags = {"Checklists"})
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody ChecklistDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.create(request));
  }

  @Operation(
      summary = "Fetch a checklist master record",
      tags = {"Checklists"})
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(checklistService.get(id));
  }

  /** Edit Checklist or Single Item (ONEMEP-34). The record type cannot change. */
  @Operation(
      summary = "Edit a checklist master record",
      tags = {"Checklists"})
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody ChecklistDto.UpdateRequest request) {
    return ResponseEntity.ok(checklistService.update(id, request));
  }

  /** Grid status toggle — saves on its own, separately from the edit form. */
  @Operation(
      summary = "Activate or deactivate a checklist master record",
      tags = {"Checklists"})
  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(checklistService.updateStatus(id, active));
  }

  /** Matching-Design count for the delete confirmation (ONEMEP-32). */
  @Operation(
      summary = "Count the Designs a checklist currently matches",
      tags = {"Checklists"})
  @GetMapping("/{id}/impact")
  public ResponseEntity<ApiResponse<?>> impact(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(checklistService.impact(id));
  }

  @Operation(
      summary = "Delete a checklist master record and its items",
      tags = {"Checklists"})
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(checklistService.delete(id));
  }

  /** Checklists applicable to a Design's Discipline/Type/Subject — used when raising approvals. */
  @Operation(
      summary = "Resolve the checklists applicable to a Discipline/Type/Subject combination",
      tags = {"Checklists"})
  @GetMapping("/applicable")
  public ResponseEntity<ApiResponse<?>> applicable(
      @RequestParam @NotNull Long disciplineId,
      @RequestParam @NotNull Long typeId,
      @RequestParam @NotNull Long subjectId) {
    return ResponseEntity.ok(checklistService.applicable(disciplineId, typeId, subjectId));
  }
}
