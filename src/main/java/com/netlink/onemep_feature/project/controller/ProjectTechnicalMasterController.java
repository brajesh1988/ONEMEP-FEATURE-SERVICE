package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.service.ProjectTechnicalMasterService;
import com.netlink.onemep_feature.project.service.ProjectTechnicalMasterService.DownloadedFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project-level Technical Master: the consolidated form plus its attachments. Delivery schedule and
 * client information are surfaced read-only within the GET payload.
 */
@RestController
@RequestMapping("/projects/{projectId}/technical-master")
@RequiredArgsConstructor
public class ProjectTechnicalMasterController {

  private final ProjectTechnicalMasterService technicalMasterService;

  /** Form definition (sections + fields) for the project's category. */
  @GetMapping("/template")
  public ResponseEntity<ApiResponse<?>> template(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(technicalMasterService.getTemplate(projectId));
  }

  /** Consolidated read; returns an {@code exists:false} shell when none created yet. */
  @GetMapping
  public ResponseEntity<ApiResponse<?>> get(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(technicalMasterService.get(projectId));
  }

  /** Read-only summary card of the saved Technical Master. */
  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<?>> summary(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(technicalMasterService.getSummary(projectId));
  }

  /** Create-or-replace the Technical Master (create & maintain). */
  @PutMapping
  public ResponseEntity<ApiResponse<?>> upsert(
      @PathVariable @NotNull Long projectId,
      @Valid @RequestBody TechnicalMasterDto.UpsertRequest request) {
    return ResponseEntity.ok(technicalMasterService.upsert(projectId, request));
  }

  @PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<?>> uploadAttachment(
      @PathVariable @NotNull Long projectId, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(technicalMasterService.uploadAttachment(projectId, file));
  }

  @GetMapping("/attachments")
  public ResponseEntity<ApiResponse<?>> listAttachments(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(technicalMasterService.listAttachments(projectId));
  }

  @GetMapping("/attachments/{attachmentId}/download")
  public ResponseEntity<Resource> downloadAttachment(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long attachmentId) {
    DownloadedFile file = technicalMasterService.downloadAttachment(projectId, attachmentId);
    MediaType contentType =
        file.contentType() == null
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(file.contentType());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .contentType(contentType)
        .contentLength(file.data().length)
        .body(new ByteArrayResource(file.data()));
  }

  @DeleteMapping("/attachments/{attachmentId}")
  public ResponseEntity<ApiResponse<?>> deleteAttachment(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long attachmentId) {
    return ResponseEntity.ok(technicalMasterService.deleteAttachment(projectId, attachmentId));
  }
}
