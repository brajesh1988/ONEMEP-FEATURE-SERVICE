package com.netlink.onemep_feature.project.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.service.ProjectSpecSheetService;
import com.netlink.onemep_feature.project.service.ProjectSpecSheetService.DownloadedFile;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Specs-sheet attachments for a project (ONEMEP-15): upload / list / download / delete. */
@RestController
@RequestMapping("/projects/{projectId}/spec-sheets")
@RequiredArgsConstructor
public class ProjectSpecSheetController {

  private final ProjectSpecSheetService specSheetService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<?>> upload(
      @PathVariable @NotNull Long projectId, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED).body(specSheetService.upload(projectId, file));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<?>> list(@PathVariable @NotNull Long projectId) {
    return ResponseEntity.ok(specSheetService.list(projectId));
  }

  @GetMapping("/{sheetId}/download")
  public ResponseEntity<Resource> download(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long sheetId) {
    DownloadedFile file = specSheetService.download(projectId, sheetId);
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

  @DeleteMapping("/{sheetId}")
  public ResponseEntity<ApiResponse<?>> delete(
      @PathVariable @NotNull Long projectId, @PathVariable @NotNull Long sheetId) {
    return ResponseEntity.ok(specSheetService.delete(projectId, sheetId));
  }
}
