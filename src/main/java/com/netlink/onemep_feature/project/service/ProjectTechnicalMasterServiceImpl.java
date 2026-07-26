package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectActivityLog;
import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import com.netlink.onemep_feature.project.model.ProjectDidSpecification;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalAttachment;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalParameter;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidSpecificationRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalAttachmentRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalParameterRepo;
import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.model.UnitMaster;
import com.netlink.onemep_feature.unit.repo.UnitRepo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  private static final Set<String> SCOPES = Set.of("COMMON", "CATEGORY_SPECIFIC");

  private final ProjectTechnicalMasterRepo technicalMasterRepo;
  private final ProjectTechnicalParameterRepo parameterRepo;
  private final ProjectDidSpecificationRepo didRepo;
  private final ProjectTechnicalAttachmentRepo attachmentRepo;
  private final ProjectRepo projectRepo;
  private final ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  private final TechnicalMasterRepo technicalFieldRepo;
  private final UnitRepo unitRepo;
  private final ProjectActivityLogRepo activityRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

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
    ProjectTechnicalMaster master = technicalMasterRepo.findByProject_Id(projectId).orElse(null);
    TechnicalMasterDto.ClientInfo clientInfo =
        new TechnicalMasterDto.ClientInfo(project.getClient(), project.getLocation());

    if (master == null) {
      TechnicalMasterDto.Summary shell =
          new TechnicalMasterDto.Summary(
              false,
              project.getId(),
              null,
              null,
              0L,
              0L,
              0L,
              0L,
              clientInfo,
              false,
              null,
              null,
              null,
              null);
      return apiResponseAdaptor.success(
          "Technical master has not been created for this project yet.", shell);
    }

    Long masterId = master.getId();
    TechnicalMasterDto.Summary summary =
        new TechnicalMasterDto.Summary(
            true,
            project.getId(),
            master.getRemarks(),
            master.getVersion(),
            parameterRepo.countByTechnicalMaster_IdAndScope(masterId, "COMMON"),
            parameterRepo.countByTechnicalMaster_IdAndScope(masterId, "CATEGORY_SPECIFIC"),
            didRepo.countByTechnicalMaster_Id(masterId),
            attachmentRepo.countByTechnicalMaster_Id(masterId),
            clientInfo,
            true,
            master.getCreatedBy(),
            master.getCreatedDate(),
            master.getUpdatedBy(),
            master.getUpdatedDate());
    return apiResponseAdaptor.success("Technical master summary fetched successfully.", summary);
  }

  @Override
  @Transactional
  public ApiResponse<?> upsert(Long projectId, TechnicalMasterDto.UpsertRequest request) {
    ProjectMaster project = requireProject(projectId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);

    ProjectTechnicalMaster master =
        technicalMasterRepo
            .findByProject_Id(projectId)
            .orElseGet(() -> newMaster(project, currentUser));
    master.setRemarks(trimToNull(request.remarks()));
    master.setUpdatedBy(currentUser);
    // Persist (and get the id for new masters) before replacing child collections.
    master = technicalMasterRepo.saveAndFlush(master);

    replaceParameters(master, request.parameters(), currentUser);
    replaceDidSpecifications(master, request.didSpecifications(), currentUser);

    logActivity(project, "TECHNICAL_MASTER_SAVED", "Technical master saved");
    log.info("Saved technicalMasterId={} for projectId={}", master.getId(), projectId);
    return apiResponseAdaptor.success(
        "Technical master saved successfully.", toResponse(project, master));
  }

  // ── Attachments ──────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ApiResponse<?> uploadAttachment(Long projectId, MultipartFile file) {
    ProjectMaster project = requireProject(projectId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);
    // An attachment can be uploaded before the form is saved; lazily create the shell master.
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
    log.info("Uploaded technicalAttachmentId={} for projectId={}", attachment.getId(), projectId);
    return apiResponseAdaptor.success("Attachment uploaded successfully.", toMetadata(attachment));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listAttachments(Long projectId) {
    requireProject(projectId);
    List<TechnicalMasterDto.AttachmentMetadata> items =
        attachmentRepo.findMetadataByProjectId(projectId);
    return apiResponseAdaptor.success("Attachments fetched successfully.", items);
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

  private ProjectTechnicalMaster newMaster(ProjectMaster project, Long currentUser) {
    ProjectTechnicalMaster master = new ProjectTechnicalMaster();
    master.setProject(project);
    master.setActive(Boolean.TRUE);
    master.setCreatedBy(currentUser);
    return master;
  }

  private void replaceParameters(
      ProjectTechnicalMaster master,
      List<TechnicalMasterDto.ParameterRequest> requests,
      Long user) {
    parameterRepo.deleteByTechnicalMaster_Id(master.getId());
    // Flush deletes before inserting replacements so Hibernate doesn't order the new INSERTs ahead
    // of the DELETEs in one flush and trip uq_tech_param (mirrors ProjectServiceImpl.replaceLeads).
    parameterRepo.flush();
    if (requests == null || requests.isEmpty()) {
      return;
    }
    Set<String> seen = new LinkedHashSet<>();
    for (TechnicalMasterDto.ParameterRequest req : requests) {
      if (req == null) {
        continue;
      }
      String scope = validateScope(req.scope());
      if (req.technicalFieldId() == null) {
        throw new ApplicationException("Parameter technicalFieldId is required.");
      }
      if (!seen.add(scope + ":" + req.technicalFieldId())) {
        throw new DuplicateResourceException(
            "Duplicate parameter for the same technical field within a scope.");
      }
      TechnicalMaster field = requireTechnicalField(req.technicalFieldId());
      UnitMaster unit = resolveUnit(req.unitId());

      ProjectTechnicalParameter parameter = new ProjectTechnicalParameter();
      parameter.setTechnicalMaster(master);
      parameter.setScope(scope);
      parameter.setTechnicalField(field);
      parameter.setUnit(unit);
      parameter.setValue(trimToNull(req.value()));
      parameter.setRemarks(trimToNull(req.remarks()));
      parameter.setCreatedBy(user);
      parameterRepo.save(parameter);
    }
  }

  private void replaceDidSpecifications(
      ProjectTechnicalMaster master, List<TechnicalMasterDto.DidRequest> requests, Long user) {
    didRepo.deleteByTechnicalMaster_Id(master.getId());
    didRepo.flush();
    if (requests == null || requests.isEmpty()) {
      return;
    }
    for (TechnicalMasterDto.DidRequest req : requests) {
      if (req == null || req.name() == null || req.name().isBlank()) {
        continue;
      }
      UnitMaster unit = resolveUnit(req.unitId());
      ProjectDidSpecification did = new ProjectDidSpecification();
      did.setTechnicalMaster(master);
      did.setName(req.name().trim());
      did.setSpecification(trimToNull(req.specification()));
      did.setUnit(unit);
      did.setRemarks(trimToNull(req.remarks()));
      did.setCreatedBy(user);
      didRepo.save(did);
    }
  }

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
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          deliverySchedule,
          clientInfo,
          null,
          null,
          null,
          null);
    }

    List<TechnicalMasterDto.ParameterResponse> common = new ArrayList<>();
    List<TechnicalMasterDto.ParameterResponse> categorySpecific = new ArrayList<>();
    for (ProjectTechnicalParameter p :
        parameterRepo.findByTechnicalMaster_IdOrderByScopeAscIdAsc(master.getId())) {
      TechnicalMasterDto.ParameterResponse mapped = toParameterResponse(p);
      if ("CATEGORY_SPECIFIC".equals(p.getScope())) {
        categorySpecific.add(mapped);
      } else {
        common.add(mapped);
      }
    }
    List<TechnicalMasterDto.DidResponse> dids =
        didRepo.findByTechnicalMaster_IdOrderByIdAsc(master.getId()).stream()
            .map(this::toDidResponse)
            .toList();
    List<TechnicalMasterDto.AttachmentMetadata> attachments =
        attachmentRepo.findMetadataByProjectId(project.getId());

    return new TechnicalMasterDto.Response(
        true,
        master.getId(),
        project.getId(),
        master.getRemarks(),
        master.getVersion(),
        master.getActive(),
        common,
        categorySpecific,
        dids,
        attachments,
        deliverySchedule,
        clientInfo,
        master.getCreatedBy(),
        master.getCreatedDate(),
        master.getUpdatedBy(),
        master.getUpdatedDate());
  }

  private TechnicalMasterDto.ParameterResponse toParameterResponse(ProjectTechnicalParameter p) {
    TechnicalMaster field = p.getTechnicalField();
    UnitMaster unit = p.getUnit();
    return new TechnicalMasterDto.ParameterResponse(
        p.getId(),
        p.getScope(),
        field == null ? null : field.getId(),
        field == null ? null : field.getName(),
        unit == null ? null : unit.getId(),
        unit == null ? null : unit.getSymbol(),
        p.getValue(),
        p.getRemarks());
  }

  private TechnicalMasterDto.DidResponse toDidResponse(ProjectDidSpecification d) {
    UnitMaster unit = d.getUnit();
    return new TechnicalMasterDto.DidResponse(
        d.getId(),
        d.getName(),
        d.getSpecification(),
        unit == null ? null : unit.getId(),
        unit == null ? null : unit.getSymbol(),
        d.getRemarks());
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

  private TechnicalMaster requireTechnicalField(Long id) {
    return technicalFieldRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Technical field not found: " + id));
  }

  private UnitMaster resolveUnit(Long unitId) {
    if (unitId == null) {
      return null;
    }
    return unitRepo
        .findById(unitId)
        .orElseThrow(() -> new ResourceNotFoundException("Unit not found: " + unitId));
  }

  private void logActivity(ProjectMaster project, String action, String detail) {
    ProjectActivityLog entry = new ProjectActivityLog();
    entry.setProject(project);
    entry.setAction(action);
    entry.setDetail(detail);
    entry.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    activityRepo.save(entry);
  }

  private static String validateScope(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    if (!SCOPES.contains(value)) {
      throw new ApplicationException("Parameter scope must be one of: COMMON, CATEGORY_SPECIFIC.");
    }
    return value;
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
