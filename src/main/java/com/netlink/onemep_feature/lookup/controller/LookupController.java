package com.netlink.onemep_feature.lookup.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.dto.LookupDto;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.service.LookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
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

/**
 * Reference-data catalogue: Discipline, Type, Subject, Floor, Zone and Stage.
 *
 * <p>Collection routes are scoped by catalogue ({@code /lookups/disciplines}); single-entry routes
 * sit under {@code /lookups/entries/{id}} because ids are unique across every catalogue.
 */
@RestController
@RequestMapping("/lookups")
@RequiredArgsConstructor
public class LookupController {

  private final LookupService lookupService;

  /** Active values for a dropdown — the route every Sprint 3 picker calls. */
  @Operation(
      summary = "List active values for a catalogue",
      tags = {"Lookups"})
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Unknown catalogue")
  })
  @GetMapping("/{type}")
  public ResponseEntity<ApiResponse<?>> listOptions(@PathVariable String type) {
    return ResponseEntity.ok(lookupService.listOptions(resolve(type)));
  }

  /** Paged, searchable listing for catalogue maintenance. */
  @Operation(
      summary = "List catalogue values with search and pagination",
      tags = {"Lookups"})
  @PostMapping("/{type}/list")
  public ResponseEntity<ApiResponse<?>> list(
      @PathVariable String type, @Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(lookupService.list(resolve(type), request));
  }

  @Operation(
      summary = "Add a value to a catalogue",
      tags = {"Lookups"})
  @PostMapping("/{type}")
  public ResponseEntity<ApiResponse<?>> create(
      @PathVariable String type, @Valid @RequestBody LookupDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(lookupService.create(resolve(type), request));
  }

  @Operation(
      summary = "Fetch a single catalogue value",
      tags = {"Lookups"})
  @GetMapping("/entries/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(lookupService.get(id));
  }

  @Operation(
      summary = "Edit a catalogue value",
      tags = {"Lookups"})
  @PutMapping("/entries/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody LookupDto.UpdateRequest request) {
    return ResponseEntity.ok(lookupService.update(id, request));
  }

  @Operation(
      summary = "Activate or deactivate a catalogue value",
      tags = {"Lookups"})
  @PatchMapping("/entries/{id}/status")
  public ResponseEntity<ApiResponse<?>> updateStatus(
      @PathVariable @NotNull Long id, @RequestParam @NotNull Boolean active) {
    return ResponseEntity.ok(lookupService.updateStatus(id, active));
  }

  /**
   * Accepts the plural, hyphenated form used in URLs ({@code disciplines}, {@code design-types}) as
   * well as the raw enum name.
   */
  private static LookupType resolve(String raw) {
    String singular =
        raw != null && raw.length() > 1 && raw.endsWith("s")
            ? raw.substring(0, raw.length() - 1)
            : raw;
    return LookupType.from(singular)
        .or(() -> LookupType.from(raw))
        .orElseThrow(() -> new ResourceNotFoundException("Unknown catalogue '" + raw + "'."));
  }
}
