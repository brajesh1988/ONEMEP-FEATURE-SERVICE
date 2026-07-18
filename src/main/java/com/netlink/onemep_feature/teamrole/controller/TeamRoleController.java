package com.netlink.onemep_feature.teamrole.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.teamrole.dto.TeamRoleDto;
import com.netlink.onemep_feature.teamrole.service.TeamRoleService;
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
@RequestMapping("/team-roles")
@RequiredArgsConstructor
public class TeamRoleController {

  private final TeamRoleService teamRoleService;

  /** Team role listing with search + pagination (ONEMEP-19). */
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(teamRoleService.list(request));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<?>> listActive() {
    return ResponseEntity.ok(teamRoleService.listActive());
  }

  /** Add Team Role mapped to a tier (ONEMEP-20). */
  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody TeamRoleDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(teamRoleService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(teamRoleService.get(id));
  }

  /** Edit Team Role (ONEMEP-21). */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody TeamRoleDto.UpdateRequest request) {
    return ResponseEntity.ok(teamRoleService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(teamRoleService.updateStatus(id, active));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(teamRoleService.delete(id));
  }
}
