package com.netlink.onemep_feature.timetracking.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.timetracking.dto.TimeTrackingDto;
import com.netlink.onemep_feature.timetracking.service.TimeTrackingService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Design time tracking (ONEMEP-42).
 *
 * <p>There is no update route. ONEMEP-42 lists "may an entry be edited after creation" as an open
 * question, and a timesheet that can be rewritten in place is the harder default to walk back —
 * delete and re-log is available in the meantime.
 */
@RestController
@RequestMapping("/designs/{designId}/time-entries")
@RequiredArgsConstructor
public class TimeTrackingController {

  private final TimeTrackingService timeTrackingService;

  @Operation(
      summary = "Fetch a Design's time tracking, grouped by user and work date",
      tags = {"Time Tracking"})
  @GetMapping
  public ResponseEntity<ApiResponse<?>> summary(@PathVariable @NotNull Long designId) {
    return ResponseEntity.ok(timeTrackingService.summary(designId));
  }

  @Operation(
      summary = "Log time against a Design",
      tags = {"Time Tracking"})
  @PostMapping
  public ResponseEntity<ApiResponse<?>> log(
      @PathVariable @NotNull Long designId,
      @Valid @RequestBody TimeTrackingDto.LogRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(timeTrackingService.log(designId, request));
  }

  @Operation(
      summary = "Delete one of your own time entries",
      tags = {"Time Tracking"})
  @DeleteMapping("/{entryId}")
  public ResponseEntity<ApiResponse<?>> delete(
      @PathVariable @NotNull Long designId, @PathVariable @NotNull Long entryId) {
    return ResponseEntity.ok(timeTrackingService.delete(designId, entryId));
  }
}
