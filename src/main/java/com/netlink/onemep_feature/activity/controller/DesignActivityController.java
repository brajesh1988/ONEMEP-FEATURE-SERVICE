package com.netlink.onemep_feature.activity.controller;

import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Design Activity audit trail (ONEMEP-43).
 *
 * <p>Read-only by design: there is no create, edit or delete route, because ONEMEP-43 requires the
 * trail to be system-generated and immutable. Entries appear only as a side effect of the business
 * operations elsewhere that produce them.
 */
@RestController
@RequestMapping("/designs/{designId}/activities")
@RequiredArgsConstructor
public class DesignActivityController {

  private final DesignActivityService designActivityService;

  @Operation(
      summary = "List a Design's activity history, newest first",
      tags = {"Designs"})
  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(
      @PathVariable @NotNull Long designId, @Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(designActivityService.list(designId, request));
  }
}
