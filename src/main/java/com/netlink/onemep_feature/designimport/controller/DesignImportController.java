package com.netlink.onemep_feature.designimport.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.designimport.service.DesignImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk Design import (ONEMEP-35).
 *
 * <p>Submission is nested under the Project, because a batch imports into exactly one register and
 * the Project scopes both duplicate rules. The status route is flat: a batch id is unique
 * service-wide, and the caller polling it should not have to remember which Project it came from.
 */
@RestController
@RequiredArgsConstructor
public class DesignImportController {

  private final DesignImportService designImportService;

  /**
   * Accepts spreadsheets and queues them.
   *
   * <p><b>202, not 201.</b> Nothing has been created yet — the response carries a batch id to poll,
   * not a resource. Several 150 MB files cannot be parsed inside the request without holding the
   * connection open for minutes and timing out at the gateway, which is why the ticket describes
   * per-file progress at all.
   */
  @Operation(
      summary = "Submit spreadsheets for bulk Design import",
      tags = {"Design Import"})
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "202",
        description = "Batch accepted; poll the returned batch id for progress"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "No files, too many files, or an unsupported file type"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Project not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "413",
        description = "A file exceeds the 150 MB limit")
  })
  @PostMapping(
      value = "/projects/{projectId}/design-imports",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<?>> submit(
      @PathVariable @NotNull Long projectId, @RequestParam("files") List<MultipartFile> files) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(designImportService.submit(projectId, files));
  }

  /** Per-file state and per-row validation errors for a submitted batch (ONEMEP-35). */
  @Operation(
      summary = "Fetch an import batch's per-file status and row errors",
      tags = {"Design Import"})
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Current state of the batch, whether or not it has finished"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Batch not found")
  })
  @GetMapping("/design-imports/{batchId}")
  public ResponseEntity<ApiResponse<?>> status(@PathVariable @NotNull Long batchId) {
    return ResponseEntity.ok(designImportService.status(batchId));
  }
}
