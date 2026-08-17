package com.netlink.onemep_feature.file.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.storage.FileStorage;
import com.netlink.onemep_feature.common.storage.StorageProperties;
import com.netlink.onemep_feature.file.dto.DesignFileDto;
import com.netlink.onemep_feature.file.model.DesignFileVersion;
import com.netlink.onemep_feature.file.service.DesignFileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploaded files, versions and per-version comments (ONEMEP-39).
 *
 * <p>Upload routes are nested under the Design; everything else is keyed by file id, since a file
 * belongs to exactly one Design and its id is unique service-wide.
 */
@RestController
@RequiredArgsConstructor
public class DesignFileController {

  private final DesignFileService designFileService;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;

  @Operation(
      summary = "List a Design's uploaded files",
      tags = {"Files"})
  @GetMapping("/designs/{designId}/files")
  public ResponseEntity<ApiResponse<?>> listFiles(@PathVariable @NotNull Long designId) {
    return ResponseEntity.ok(designFileService.listFiles(designId));
  }

  /** Multi-file upload; each file becomes its own logical file and succeeds or fails on its own. */
  @Operation(
      summary = "Upload one or more files to a Design",
      tags = {"Files"})
  @PostMapping(value = "/designs/{designId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<?>> upload(
      @PathVariable @NotNull Long designId,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(value = "note", required = false) String note) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(designFileService.upload(designId, files, note));
  }

  @Operation(
      summary = "Upload a new version of an existing file",
      tags = {"Files"})
  @PostMapping(value = "/files/{fileId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<?>> uploadNewVersion(
      @PathVariable @NotNull Long fileId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "note", required = false) String note) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(designFileService.uploadNewVersion(fileId, file, note));
  }

  @Operation(
      summary = "List a file's version history, newest first",
      tags = {"Files"})
  @GetMapping("/files/{fileId}/versions")
  public ResponseEntity<ApiResponse<?>> listVersions(@PathVariable @NotNull Long fileId) {
    return ResponseEntity.ok(designFileService.listVersions(fileId));
  }

  /**
   * Downloads the exact revision requested — never the current one unless that is what was asked
   * for.
   *
   * <p>Where the provider can sign a URL the response is a redirect, so the transfer goes straight
   * from object storage instead of occupying a request thread here. Authorisation has already run
   * by this point, which matters: a signed URL bypasses the security filter chain thereafter.
   */
  @Operation(
      summary = "Download a specific file version",
      tags = {"Files"})
  @GetMapping("/files/{fileId}/versions/{versionId}/content")
  public ResponseEntity<?> download(
      @PathVariable @NotNull Long fileId, @PathVariable @NotNull Long versionId) {
    DesignFileVersion version = designFileService.requireVersionForDownload(fileId, versionId);

    if (fileStorage.supportsPresignedUrls()) {
      URI url =
          fileStorage.presignedGetUrl(
              version.key(), Duration.ofSeconds(storageProperties.presignedUrlTtlSeconds()));
      return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
    }

    InputStream stream = fileStorage.open(version.key());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(version.getOriginalFilename()))
        .contentType(mediaType(version.getContentType()))
        .contentLength(version.getSizeBytes())
        .body(new InputStreamResource(stream));
  }

  @Operation(
      summary = "Delete a file and all of its versions",
      tags = {"Files"})
  @DeleteMapping("/files/{fileId}")
  public ResponseEntity<ApiResponse<?>> deleteFile(@PathVariable @NotNull Long fileId) {
    return ResponseEntity.ok(designFileService.deleteFile(fileId));
  }

  @Operation(
      summary = "Delete a retained file version",
      tags = {"Files"})
  @DeleteMapping("/files/{fileId}/versions/{versionId}")
  public ResponseEntity<ApiResponse<?>> deleteVersion(
      @PathVariable @NotNull Long fileId, @PathVariable @NotNull Long versionId) {
    return ResponseEntity.ok(designFileService.deleteVersion(fileId, versionId));
  }

  @Operation(
      summary = "List a file's comments, grouped by version",
      tags = {"Files"})
  @GetMapping("/files/{fileId}/comments")
  public ResponseEntity<ApiResponse<?>> listComments(@PathVariable @NotNull Long fileId) {
    return ResponseEntity.ok(designFileService.listComments(fileId));
  }

  @Operation(
      summary = "Comment on a specific file version",
      tags = {"Files"})
  @PostMapping("/files/{fileId}/versions/{versionId}/comments")
  public ResponseEntity<ApiResponse<?>> addComment(
      @PathVariable @NotNull Long fileId,
      @PathVariable @NotNull Long versionId,
      @Valid @RequestBody DesignFileDto.AddCommentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(designFileService.addComment(fileId, versionId, request));
  }

  @Operation(
      summary = "Resolve or reopen a file comment",
      tags = {"Files"})
  @PatchMapping("/file-comments/{commentId}")
  public ResponseEntity<ApiResponse<?>> updateComment(
      @PathVariable @NotNull Long commentId,
      @Valid @RequestBody DesignFileDto.UpdateCommentRequest request) {
    return ResponseEntity.ok(designFileService.updateComment(commentId, request));
  }

  /** Quotes and escapes the filename so a comma or quote in it cannot break the header. */
  private static String contentDisposition(String filename) {
    String safe = filename.replace("\"", "");
    return "attachment; filename=\""
        + safe
        + "\"; filename*=UTF-8''"
        + java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static MediaType mediaType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (org.springframework.http.InvalidMediaTypeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }
}
