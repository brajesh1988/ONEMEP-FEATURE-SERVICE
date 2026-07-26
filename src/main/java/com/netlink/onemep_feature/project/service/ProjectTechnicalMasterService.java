package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project-level Technical Master: consolidated read + create-or-replace + attachments (ONEMEP-29).
 */
public interface ProjectTechnicalMasterService {

  /** The form definition (sections + fields) for the project's category. */
  ApiResponse<?> getTemplate(Long projectId);

  /** Consolidated read; returns a {@code exists:false} shell when none created yet. */
  ApiResponse<?> get(Long projectId);

  /** Compact read-only summary (counts + audit) for ONEMEP-30. */
  ApiResponse<?> getSummary(Long projectId);

  /** Create-or-replace the Technical Master (root + parameters + DID) for a project. */
  ApiResponse<?> upsert(Long projectId, TechnicalMasterDto.UpsertRequest request);

  ApiResponse<?> uploadAttachment(Long projectId, MultipartFile file);

  ApiResponse<?> listAttachments(Long projectId);

  DownloadedFile downloadAttachment(Long projectId, Long attachmentId);

  ApiResponse<?> deleteAttachment(Long projectId, Long attachmentId);

  /** Internal carrier for a downloaded file (not serialized as JSON). */
  record DownloadedFile(String fileName, String contentType, byte[] data) {}
}
