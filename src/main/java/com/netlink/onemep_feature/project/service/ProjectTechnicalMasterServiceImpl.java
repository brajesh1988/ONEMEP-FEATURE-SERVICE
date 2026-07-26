package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectActivityLog;
import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalAttachment;
import com.netlink.onemep_feature.project.model.ProjectTechnicalFieldValue;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import com.netlink.onemep_feature.project.model.TmField;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalAttachmentRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalFieldValueRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import com.netlink.onemep_feature.project.repo.TmFieldRepo;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTechnicalMasterServiceImpl implements ProjectTechnicalMasterService {

  /** Belt-and-braces server-side size guard (the servlet container also enforces 150 MB). */
  private static final long MAX_SIZE_BYTES = 150L * 1024 * 1024;

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");

  private final ProjectTechnicalMasterRepo technicalMasterRepo;
  private final ProjectTechnicalFieldValueRepo fieldValueRepo;
  private final TmFieldRepo tmFieldRepo;
  private final ProjectTechnicalAttachmentRepo attachmentRepo;
  private final ProjectRepo projectRepo;
  private final ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  private final ProjectActivityLogRepo activityRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  // ── Template ───────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> getTemplate(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    Integer series = seriesCodeOf(project);
    List<TmField> fields = series == null ? List.of() : tmFieldRepo.findByCategorySeries(series);

    Map<String, List<TechnicalMasterDto.Field>> bySection = new LinkedHashMap<>();
    for (TmField f : fields) {
      bySection
          .computeIfAbsent(f.getSection(), k -> new java.util.ArrayList<>())
          .add(
              new TechnicalMasterDto.Field(
                  f.getFieldKey(),
                  f.getLabel(),
                  f.getUnit(),
                  f.getDataType(),
                  Boolean.TRUE.equals(f.getCore()),
                  f.getFeeds(),
                  f.getNotes()));
    }
    List<TechnicalMasterDto.Section> sections =
        bySection.entrySet().stream()
            .map(e -> new TechnicalMasterDto.Section(e.getKey(), e.getValue()))
            .toList();
    return apiResponseAdaptor.success(
        "Technical master template fetched successfully.",
        new TechnicalMasterDto.Template(projectId, series, sections));
  }

  // ── Read ───────────────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    ProjectTechnicalMaster master = technicalMasterRepo.findByProject_Id(projectId).orElse(null);
    return apiResponseAdaptor.success(
        master == null
            ? "Technical master has not been created for this project yet."
            : "Technical master fetched successfully.",
        toResponse(project, master));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> getSummary(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    Integer series = seriesCodeOf(project);
    List<TmField> template = series == null ? List.of() : tmFieldRepo.findByCategorySeries(series);
    long totalFields = template.size();
    long sectionCount = template.stream().map(TmField::getSection).distinct().count();

    ProjectTechnicalMaster master = technicalMasterRepo.findByProject_Id(projectId).orElse(null);
    TechnicalMasterDto.ClientInfo clientInfo =
        new TechnicalMasterDto.ClientInfo(project.getClient(), project.getLocation());

    if (master == null) {
      return apiResponseAdaptor.success(
          "Technical master has not been created for this project yet.",
          new TechnicalMasterDto.Summary(
              false,
              projectId,
              null,
              totalFields,
              0L,
              sectionCount,
              0L,
              clientInfo,
              false,
              null,
              null,
              null,
              null));
    }
    long filled = fieldValueRepo.findByTechnicalMaster_Id(master.getId()).size();
    long attachments = attachmentRepo.countByTechnicalMaster_Id(master.getId());
    return apiResponseAdaptor.success(
        "Technical master summary fetched successfully.",
        new TechnicalMasterDto.Summary(
            true,
            projectId,
            master.getRemarks(),
            totalFields,
            filled,
            sectionCount,
            attachments,
            clientInfo,
            true,
            master.getCreatedBy(),
            master.getCreatedDate(),
            master.getUpdatedBy(),
            master.getUpdatedDate()));
  }

  // ── Create / maintain ──────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> upsert(Long projectId, TechnicalMasterDto.UpsertRequest request) {
    ProjectMaster project = requireProject(projectId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    Integer series = seriesCodeOf(project);
    Set<String> templateKeys =
        series == null
            ? Set.of()
            : tmFieldRepo.findByCategorySeries(series).stream()
                .map(TmField::getFieldKey)
                .collect(Collectors.toSet());

    ProjectTechnicalMaster master =
        technicalMasterRepo
            .findByProject_Id(projectId)
            .orElseGet(() -> newMaster(project, currentUser));
    master.setRemarks(trimToNull(request.remarks()));
    master.setUpdatedBy(currentUser);
    master = technicalMasterRepo.saveAndFlush(master);

    Map<String, String> values = request.values() == null ? Map.of() : request.values();
    for (String key : values.keySet()) {
      if (!templateKeys.contains(key)) {
        throw new ApplicationException("Unknown technical field for this category: " + key);
      }
    }

    fieldValueRepo.deleteByTechnicalMaster_Id(master.getId());
    // Flush deletes before inserting replacements to avoid an INSERT-before-DELETE clash on
    // uq_tm_value (mirrors the pattern in ProjectServiceImpl.replaceLeads).
    fieldValueRepo.flush();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      String value = trimToNull(entry.getValue());
      if (value == null) {
        continue;
      }
      ProjectTechnicalFieldValue row = new ProjectTechnicalFieldValue();
      row.setTechnicalMaster(master);
      row.setFieldKey(entry.getKey());
      row.setValue(value);
      row.setCreatedBy(currentUser);
      fieldValueRepo.save(row);
    }

    logActivity(project, "TECHNICAL_MASTER_SAVED", "Technical master saved");
    log.info("Saved technicalMasterId={} for projectId={}", master.getId(), projectId);
    return apiResponseAdaptor.success(
        "Technical master saved successfully.", toResponse(project, master));
  }

  // ── Attachments ────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> uploadAttachment(Long projectId, MultipartFile file) {
    ProjectMaster project = requireProject(projectId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    ProjectTechnicalMaster master =
        technicalMasterRepo
            .findByProject_Id(projectId)
            .orElseGet(() -> technicalMasterRepo.save(newMaster(project, currentUser)));

    if (file == null || file.isEmpty()) {
      throw new ApplicationException("A file is required.");
    }
    String originalName = file.getOriginalFilename();
    if (originalName == null || originalName.isBlank()) {
      throw new ApplicationException("The uploaded file must have a name.");
    }
    String extension = extensionOf(originalName);
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new ApplicationException("Only .doc, .docx and .pdf files are allowed.");
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new ApplicationException("The file exceeds the maximum allowed size of 150 MB.");
    }

    ProjectTechnicalAttachment attachment = new ProjectTechnicalAttachment();
    attachment.setTechnicalMaster(master);
    attachment.setFileName(originalName);
    attachment.setContentType(file.getContentType());
    attachment.setFileExtension(extension);
    attachment.setFileSize(file.getSize());
    try {
      attachment.setFileData(file.getBytes());
    } catch (IOException ex) {
      throw new ApplicationException("Failed to read the uploaded file.");
    }
    attachment.setCreatedBy(currentUser);
    attachment = attachmentRepo.save(attachment);
    logActivity(project, "TECHNICAL_ATTACHMENT_UPLOADED", originalName);
    return apiResponseAdaptor.success("Attachment uploaded successfully.", toMetadata(attachment));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listAttachments(Long projectId) {
    requireProject(projectId);
    return apiResponseAdaptor.success(
        "Attachments fetched successfully.", attachmentRepo.findMetadataByProjectId(projectId));
  }

  @Override
  @Transactional(readOnly = true)
  public DownloadedFile downloadAttachment(Long projectId, Long attachmentId) {
    ProjectTechnicalAttachment attachment = requireAttachment(projectId, attachmentId);
    return new DownloadedFile(
        attachment.getFileName(), attachment.getContentType(), attachment.getFileData());
  }

  @Override
  @Transactional
  public ApiResponse<?> deleteAttachment(Long projectId, Long attachmentId) {
    ProjectTechnicalAttachment attachment = requireAttachment(projectId, attachmentId);
    String name = attachment.getFileName();
    attachmentRepo.delete(attachment);
    logActivity(attachment.getTechnicalMaster().getProject(), "TECHNICAL_ATTACHMENT_DELETED", name);
    return apiResponseAdaptor.success("Attachment deleted successfully.");
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private TechnicalMasterDto.Response toResponse(
      ProjectMaster project, ProjectTechnicalMaster master) {
    List<DeliveryScheduleDto.Response> deliverySchedule =
        deliveryScheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(project.getId()).stream()
            .map(this::toDeliveryResponse)
            .toList();
    TechnicalMasterDto.ClientInfo clientInfo =
        new TechnicalMasterDto.ClientInfo(project.getClient(), project.getLocation());

    if (master == null) {
      return new TechnicalMasterDto.Response(
          false,
          null,
          project.getId(),
          null,
          Map.of(),
          List.of(),
          deliverySchedule,
          clientInfo,
          null,
          null,
          null,
          null);
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (ProjectTechnicalFieldValue v : fieldValueRepo.findByTechnicalMaster_Id(master.getId())) {
      values.put(v.getFieldKey(), v.getValue());
    }
    List<TechnicalMasterDto.AttachmentMetadata> attachments =
        attachmentRepo.findMetadataByProjectId(project.getId());
    return new TechnicalMasterDto.Response(
        true,
        master.getId(),
        project.getId(),
        master.getRemarks(),
        values,
        attachments,
        deliverySchedule,
        clientInfo,
        master.getCreatedBy(),
        master.getCreatedDate(),
        master.getUpdatedBy(),
        master.getUpdatedDate());
  }

  private DeliveryScheduleDto.Response toDeliveryResponse(ProjectDeliverySchedule d) {
    return new DeliveryScheduleDto.Response(
        d.getId(),
        d.getMilestone(),
        d.getDeliverable(),
        d.getPlannedDate(),
        d.getActualDate(),
        d.getStatus(),
        d.getRemarks(),
        d.getUpdatedBy(),
        d.getUpdatedDate());
  }

  private static TechnicalMasterDto.AttachmentMetadata toMetadata(ProjectTechnicalAttachment a) {
    return new TechnicalMasterDto.AttachmentMetadata(
        a.getId(),
        a.getFileName(),
        a.getContentType(),
        a.getFileExtension(),
        a.getFileSize(),
        a.getCreatedBy(),
        a.getCreatedDate());
  }

  private ProjectTechnicalMaster newMaster(ProjectMaster project, Long currentUser) {
    ProjectTechnicalMaster master = new ProjectTechnicalMaster();
    master.setProject(project);
    master.setActive(Boolean.TRUE);
    master.setCreatedBy(currentUser);
    return master;
  }

  private static Integer seriesCodeOf(ProjectMaster project) {
    return project.getCategory() == null ? null : project.getCategory().getSeriesCode();
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private ProjectTechnicalAttachment requireAttachment(Long projectId, Long attachmentId) {
    return attachmentRepo
        .findByIdAndTechnicalMaster_Project_Id(attachmentId, projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Attachment not found."));
  }

  private void logActivity(ProjectMaster project, String action, String detail) {
    ProjectActivityLog entry = new ProjectActivityLog();
    entry.setProject(project);
    entry.setAction(action);
    entry.setDetail(detail);
    entry.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    activityRepo.save(entry);
  }

  private static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot + 1).toLowerCase();
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
