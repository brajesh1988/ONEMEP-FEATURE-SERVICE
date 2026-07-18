package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.StakeholderDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectStakeholder;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectStakeholderRepo;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectStakeholderServiceImpl implements ProjectStakeholderService {

  private static final Set<String> ROLES =
      Set.of(
          "PROJECT_HEAD", "ARCHITECT", "STRUCTURE", "CLIENT", "CONSULTANT", "CONTRACTOR", "OTHER");

  private final ProjectStakeholderRepo stakeholderRepo;
  private final ProjectRepo projectRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(Long projectId) {
    requireProject(projectId);
    List<StakeholderDto.Response> items =
        stakeholderRepo.findByProject_IdOrderByRoleAscNameAsc(projectId).stream()
            .map(this::toResponse)
            .toList();
    return apiResponseAdaptor.success("Stakeholders fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(Long projectId, StakeholderDto.CreateRequest request) {
    ProjectMaster project = requireProject(projectId);
    ProjectStakeholder stakeholder = new ProjectStakeholder();
    stakeholder.setProject(project);
    stakeholder.setRole(validateRole(request.role()));
    stakeholder.setName(request.name().trim());
    stakeholder.setOrganization(trimToNull(request.organization()));
    stakeholder.setEmail(trimToNull(request.email()));
    stakeholder.setPhone(trimToNull(request.phone()));
    stakeholder.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    stakeholder = stakeholderRepo.save(stakeholder);
    log.info("Created stakeholderId={} for projectId={}", stakeholder.getId(), projectId);
    return apiResponseAdaptor.success("Stakeholder added successfully.", toResponse(stakeholder));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(
      Long projectId, Long stakeholderId, StakeholderDto.UpdateRequest request) {
    ProjectStakeholder stakeholder = requireStakeholder(projectId, stakeholderId);
    stakeholder.setRole(validateRole(request.role()));
    stakeholder.setName(request.name().trim());
    stakeholder.setOrganization(trimToNull(request.organization()));
    stakeholder.setEmail(trimToNull(request.email()));
    stakeholder.setPhone(trimToNull(request.phone()));
    stakeholder.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    stakeholderRepo.save(stakeholder);
    return apiResponseAdaptor.success("Stakeholder updated successfully.", toResponse(stakeholder));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long projectId, Long stakeholderId) {
    ProjectStakeholder stakeholder = requireStakeholder(projectId, stakeholderId);
    stakeholderRepo.delete(stakeholder);
    return apiResponseAdaptor.success("Stakeholder removed successfully.");
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private ProjectStakeholder requireStakeholder(Long projectId, Long stakeholderId) {
    return stakeholderRepo
        .findByIdAndProject_Id(stakeholderId, projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Stakeholder not found."));
  }

  private static String validateRole(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    if (!ROLES.contains(value)) {
      throw new ApplicationException(
          "Role must be one of: PROJECT_HEAD, ARCHITECT, STRUCTURE, CLIENT, CONSULTANT, CONTRACTOR,"
              + " OTHER.");
    }
    return value;
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private StakeholderDto.Response toResponse(ProjectStakeholder s) {
    return new StakeholderDto.Response(
        s.getId(),
        s.getRole(),
        s.getName(),
        s.getOrganization(),
        s.getEmail(),
        s.getPhone(),
        s.getUpdatedBy(),
        s.getUpdatedDate());
  }
}
