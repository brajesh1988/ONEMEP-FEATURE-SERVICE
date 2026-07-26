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
import com.netlink.onemep_feature.project.model.TmSection;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalAttachmentRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalFieldValueRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import com.netlink.onemep_feature.project.repo.TmFieldRepo;
import com.netlink.onemep_feature.project.repo.TmSectionRepo;
import java.io.IOException;
import java.util.ArrayList;
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

  private static final long MAX_SIZE_BYTES = 150L * 1024 * 1024;
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");

  private final ProjectTechnicalMasterRepo technicalMasterRepo;
  private final ProjectTechnicalFieldValueRepo fieldValueRepo;
  private final TmSectionRepo sectionRepo;
  private final TmFieldRepo fieldRepo;
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
    return apiResponseAdaptor.success(
        "Technical master template fetched successfully.", buildTemplate(project));
  }

  private TechnicalMasterDto.Template buildTemplate(ProjectMaster project) {
    Integer series = seriesCodeOf(project);
    List<TechnicalMasterDto.Section> sections = new ArrayList<>();
    if (series != null) {
      for (TmSection s : sectionRepo.findBySeriesCodeOrderBySectionOrderAsc(series)) {
        List<TechnicalMasterDto.Field> fields =
            fieldRepo.findBySection_IdOrderByFieldOrderAsc(s.getId()).stream()
                .map(this::toField)
                .toList();
        sections.add(
            new TechnicalMasterDto.Section(
                s.getId(),
                s.getTitle(),
                s.getSectionOrder() == null ? 0 : s.getSectionOrder(),
                Boolean.TRUE.equals(s.getActive()),
                !Boolean.TRUE.equals(s.getSystem()),
                fields));
      }
    }
    return new TechnicalMasterDto.Template(project.getId(), series, sections);
  }

  private TechnicalMasterDto.Field toField(TmField f) {
    return new TechnicalMasterDto.Field(
        f.getId(),
        f.getFieldKey(),
        f.getLabel(),
        f.getUnit(),
        f.getDataType(),
        Boolean.TRUE.equals(f.getRequired()),
        f.getFeeds(),
        f.getNotes());
  }

  // ── Catalog edits ──────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> createSection(Long projectId, TechnicalMasterDto.SectionRequest request) {
    ProjectMaster project = requireProject(projectId);
    Integer series = requireSeries(project);
    TmSection section = new TmSection();
    section.setSeriesCode(series);
    section.setTitle(request.title().trim());
    section.setSectionOrder((int) sectionRepo.countBySeriesCode(series) + 1);
    section.setActive(request.active() == null ? Boolean.TRUE : request.active());
    section.setSystem(Boolean.FALSE); // user-added heads are deletable
    sectionRepo.save(section);
    return apiResponseAdaptor.success("Section added.", buildTemplate(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateSection(
      Long projectId, Long sectionId, TechnicalMasterDto.SectionRequest request) {
    ProjectMaster project = requireProject(projectId);
    TmSection section = requireSection(project, sectionId);
    if (request.title() != null && !request.title().isBlank()) {
      section.setTitle(request.title().trim());
    }
    if (request.active() != null) {
      section.setActive(request.active());
    }
    sectionRepo.save(section);
    return apiResponseAdaptor.success("Section updated.", buildTemplate(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> deleteSection(Long projectId, Long sectionId) {
    ProjectMaster project = requireProject(projectId);
    TmSection section = requireSection(project, sectionId);
    if (Boolean.TRUE.equals(section.getSystem())) {
      throw new ApplicationException(
          "Standard heads cannot be deleted; turn off \"In project\" to hide them.");
    }
    fieldRepo.deleteBySection_Id(section.getId());
    fieldRepo.flush();
    sectionRepo.delete(section);
    return apiResponseAdaptor.success("Section deleted.", buildTemplate(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> createField(Long projectId, TechnicalMasterDto.FieldRequest request) {
    ProjectMaster project = requireProject(projectId);
    Integer series = requireSeries(project);
    TmSection section = requireSection(project, request.sectionId());

    TmField field = new TmField();
    field.setSection(section);
    field.setSeriesCode(series);
    field.setLabel(request.label().trim());
    field.setFieldKey(uniqueKey(series, section.getTitle(), request.label()));
    field.setUnit(trimToNull(request.unit()));
    field.setDataType(normalizeDataType(request.dataType()));
    field.setRequired(Boolean.TRUE.equals(request.required()));
    field.setFeeds("REF");
    field.setFieldOrder((int) fieldRepo.countBySection_Id(section.getId()) + 1);
    fieldRepo.save(field);
    return apiResponseAdaptor.success("Field added.", buildTemplate(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateField(
      Long projectId, Long fieldId, TechnicalMasterDto.FieldRequest request) {
    ProjectMaster project = requireProject(projectId);
    TmField field = requireField(project, fieldId);
    if (request.label() != null && !request.label().isBlank()) {
      field.setLabel(request.label().trim());
    }
    if (request.unit() != null) {
      field.setUnit(trimToNull(request.unit()));
    }
    if (request.dataType() != null) {
      field.setDataType(normalizeDataType(request.dataType()));
    }
    if (request.required() != null) {
      field.setRequired(request.required());
    }
    fieldRepo.save(field);
    return apiResponseAdaptor.success("Field updated.", buildTemplate(project));
  }

  @Override
  @Transactional
  public ApiResponse<?> deleteField(Long projectId, Long fieldId) {
    ProjectMaster project = requireProject(projectId);
    TmField field = requireField(project, fieldId);
    fieldRepo.delete(field);
    return apiResponseAdaptor.success("Field deleted.", buildTemplate(project));
  }

  // ── Values ─────────────────────────────────────────────────────────────────

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
  @Transactional
  public ApiResponse<?> upsert(Long projectId, TechnicalMasterDto.UpsertRequest request) {
    ProjectMaster project = requireProject(projectId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    Integer series = seriesCodeOf(project);

    List<TmField> catalogFields = series == null ? List.of() : fieldRepo.findBySeriesCode(series);
    Set<String> knownKeys =
        catalogFields.stream().map(TmField::getFieldKey).collect(Collectors.toSet());

    Map<String, String> values = request.values() == null ? Map.of() : request.values();
    for (String key : values.keySet()) {
      if (!knownKeys.contains(key)) {
        throw new ApplicationException("Unknown technical field: " + key);
      }
    }

    // ONEMEP-29: mandatory-field validation — every required field of a head that is in the
    // project must have a value. Heads switched off are not part of the sheet.
    List<String> missing = new ArrayList<>();
    for (TmField f : catalogFields) {
      if (Boolean.TRUE.equals(f.getRequired()) && Boolean.TRUE.equals(f.getSection().getActive())) {
        String v = trimToNull(values.get(f.getFieldKey()));
        if (v == null) {
          missing.add(f.getLabel());
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new ApplicationException(
          "Please fill all required fields before saving: " + String.join(", ", missing));
    }

    ProjectTechnicalMaster master =
        technicalMasterRepo
            .findByProject_Id(projectId)
            .orElseGet(() -> newMaster(project, currentUser));
    master.setRemarks(trimToNull(request.remarks()));
    master.setUpdatedBy(currentUser);
    master = technicalMasterRepo.saveAndFlush(master);

    fieldValueRepo.deleteByTechnicalMaster_Id(master.getId());
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
    return apiResponseAdaptor.success(
        "Technical master saved successfully.", toResponse(project, master));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> getSummary(Long projectId) {
    ProjectMaster project = requireProject(projectId);
    Integer series = seriesCodeOf(project);
    long totalFields =
        series == null
            ? 0
            : fieldRepo.findBySeriesCode(series).stream()
                .filter(f -> Boolean.TRUE.equals(f.getSection().getActive()))
                .count();
    long sectionCount =
        series == null
            ? 0
            : sectionRepo.findBySeriesCodeOrderBySectionOrderAsc(series).stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .count();

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

  private static Integer requireSeries(ProjectMaster project) {
    Integer series = seriesCodeOf(project);
    if (series == null) {
      throw new ApplicationException(
          "The project's category has no series code, so its technical master form cannot be"
              + " edited.");
    }
    return series;
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private TmSection requireSection(ProjectMaster project, Long sectionId) {
    TmSection section =
        sectionRepo
            .findById(sectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Section not found."));
    if (!section.getSeriesCode().equals(seriesCodeOf(project))) {
      throw new ApplicationException("Section does not belong to this project's category.");
    }
    return section;
  }

  private TmField requireField(ProjectMaster project, Long fieldId) {
    TmField field =
        fieldRepo
            .findById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("Field not found."));
    if (!field.getSeriesCode().equals(seriesCodeOf(project))) {
      throw new ApplicationException("Field does not belong to this project's category.");
    }
    return field;
  }

  private ProjectTechnicalAttachment requireAttachment(Long projectId, Long attachmentId) {
    return attachmentRepo
        .findByIdAndTechnicalMaster_Project_Id(attachmentId, projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Attachment not found."));
  }

  private String uniqueKey(Integer series, String sectionTitle, String label) {
    String base = slug(sectionTitle) + "__" + slug(label);
    String key = base;
    int n = 2;
    while (fieldRepo.existsBySeriesCodeAndFieldKey(series, key)) {
      key = base + "_" + n++;
    }
    return key;
  }

  private static String slug(String raw) {
    if (raw == null) {
      return "field";
    }
    String s = raw.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    return s.isEmpty() ? "field" : s;
  }

  private static String normalizeDataType(String raw) {
    return "NUMBER".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "NUMBER" : "TEXT";
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
