package com.netlink.onemep_feature.technical.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.technical.dto.TechnicalDto;
import com.netlink.onemep_feature.technical.service.TechnicalService;
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

/** PROVISIONAL controller for Technical Master (ONEMEP-29). */
@RestController
@RequestMapping("/technical-master")
@RequiredArgsConstructor
public class TechnicalController {

  private final TechnicalService technicalService;

  @PostMapping("/list")
  public ResponseEntity<ApiResponse<?>> list(@Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(technicalService.list(request));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> create(
      @Valid @RequestBody TechnicalDto.CreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(technicalService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(technicalService.get(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody TechnicalDto.UpdateRequest request) {
    return ResponseEntity.ok(technicalService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<?>> delete(@PathVariable @NotNull Long id) {
    return ResponseEntity.ok(technicalService.delete(id));
  }
}
