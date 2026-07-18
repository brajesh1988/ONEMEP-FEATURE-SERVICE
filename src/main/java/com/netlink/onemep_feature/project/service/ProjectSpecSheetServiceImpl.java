package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.SpecSheetDto;
import com.netlink.onemep_feature.project.model.ProjectActivityLog;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectSpecSheet;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectSpecSheetRepo;
import java.io.IOException;
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
public class ProjectSpecSheetServiceImpl implements ProjectSpecSheetService {

  /** Belt-and-braces server-side size guard (the servlet container also enforces 150 MB). */
  private static final long MAX_SIZE_BYTES = 150L * 1024 * 1024;

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");

  private final ProjectSpecSheetRepo specSheetRepo;
  private final ProjectRepo projectRepo;
  private final ProjectActivityLogRepo activityRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional
  public ApiResponse<?> upload(Long projectId, MultipartFile file) {
    ProjectMaster project = requireProject(projectId);
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

    ProjectSpecSheet sheet = new ProjectSpecSheet();
    sheet.setProject(project);
    sheet.setFileName(originalName);
    sheet.setContentType(file.getContentType());
    sheet.setFileExtension(extension);
    sheet.setFileSize(file.getSize());
    try {
      sheet.setFileData(file.getBytes());
    } catch (IOException ex) {
      throw new ApplicationException("Failed to read the uploaded file.");
    }
    sheet.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    sheet = specSheetRepo.save(sheet);
    logActivity(project, "SPEC_SHEET_UPLOADED", originalName);
    log.info("Uploaded specSheetId={} for projectId={}", sheet.getId(), projectId);
    return apiResponseAdaptor.success("Specs sheet uploaded successfully.", toMetadata(sheet));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(Long projectId) {
    requireProject(projectId);
    List<SpecSheetDto.Metadata> items = specSheetRepo.findMetadataByProjectId(projectId);
    return apiResponseAdaptor.success("Specs sheets fetched successfully.", items);
  }

  @Override
  @Transactional(readOnly = true)
  public DownloadedFile download(Long projectId, Long sheetId) {
    ProjectSpecSheet sheet =
        specSheetRepo
            .findByIdAndProject_Id(sheetId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Specs sheet not found."));
    return new DownloadedFile(sheet.getFileName(), sheet.getContentType(), sheet.getFileData());
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long projectId, Long sheetId) {
    ProjectSpecSheet sheet =
        specSheetRepo
            .findByIdAndProject_Id(sheetId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Specs sheet not found."));
    String name = sheet.getFileName();
    specSheetRepo.delete(sheet);
    logActivity(sheet.getProject(), "SPEC_SHEET_DELETED", name);
    return apiResponseAdaptor.success("Specs sheet deleted successfully.");
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
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

  private static SpecSheetDto.Metadata toMetadata(ProjectSpecSheet s) {
    return new SpecSheetDto.Metadata(
        s.getId(),
        s.getFileName(),
        s.getContentType(),
        s.getFileExtension(),
        s.getFileSize(),
        s.getCreatedBy(),
        s.getCreatedDate());
  }
}
