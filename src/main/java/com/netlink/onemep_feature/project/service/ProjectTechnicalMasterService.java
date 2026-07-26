package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project-level Technical Master (ONEMEP-29): an editable, category-driven form (sections +
 * fields), per-project values with mandatory-field validation, and attachments.
 */
public interface ProjectTechnicalMasterService {

  /** The editable form (all sections + fields, with active flags) for the project's category. */
  ApiResponse<?> getTemplate(Long projectId);

  // ── Catalog edits (affect the whole category / all its projects) ────────────

  ApiResponse<?> createSection(Long projectId, TechnicalMasterDto.SectionRequest request);

  ApiResponse<?> updateSection(
      Long projectId, Long sectionId, TechnicalMasterDto.SectionRequest request);

  ApiResponse<?> deleteSection(Long projectId, Long sectionId);

  ApiResponse<?> createField(Long projectId, TechnicalMasterDto.FieldRequest request);

  ApiResponse<?> updateField(Long projectId, Long fieldId, TechnicalMasterDto.FieldRequest request);

  ApiResponse<?> deleteField(Long projectId, Long fieldId);

  // ── Values ──────────────────────────────────────────────────────────────────

  /** Consolidated read; returns a {@code exists:false} shell when none created yet. */
  ApiResponse<?> get(Long projectId);

  /**
   * Create-or-replace values; rejects unknown keys and blocks save if a required field is empty.
   */
  ApiResponse<?> upsert(Long projectId, TechnicalMasterDto.UpsertRequest request);

  /** Compact read-only summary (counts + audit). */
  ApiResponse<?> getSummary(Long projectId);

  // ── Attachments ─────────────────────────────────────────────────────────────

  ApiResponse<?> uploadAttachment(Long projectId, MultipartFile file);

  ApiResponse<?> listAttachments(Long projectId);

  DownloadedFile downloadAttachment(Long projectId, Long attachmentId);

  ApiResponse<?> deleteAttachment(Long projectId, Long attachmentId);

  /** Internal carrier for a downloaded file (not serialized as JSON). */
  record DownloadedFile(String fileName, String contentType, byte[] data) {}
}
