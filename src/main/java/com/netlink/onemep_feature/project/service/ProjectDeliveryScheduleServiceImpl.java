package com.netlink.onemep_feature.project.service;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.DeliveryScheduleDto;
import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDeliveryScheduleServiceImpl implements ProjectDeliveryScheduleService {

  private static final Set<String> STATUSES =
      Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "DELAYED");

  private final ProjectDeliveryScheduleRepo scheduleRepo;
  private final ProjectRepo projectRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(Long projectId) {
    requireProject(projectId);
    List<DeliveryScheduleDto.Response> items =
        scheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(projectId).stream()
            .map(this::toResponse)
            .toList();
    return apiResponseAdaptor.success("Delivery schedule fetched successfully.", items);
  }

  @Override
  @Transactional
  public ApiResponse<?> create(Long projectId, DeliveryScheduleDto.CreateRequest request) {
    ProjectMaster project = requireProject(projectId);
    ProjectDeliverySchedule item = new ProjectDeliverySchedule();
    item.setProject(project);
    item.setMilestone(request.milestone().trim());
    item.setDeliverable(trimToNull(request.deliverable()));
    item.setPlannedDate(request.plannedDate());
    item.setActualDate(request.actualDate());
    item.setStatus(validateStatus(request.status()));
    item.setRemarks(trimToNull(request.remarks()));
    item.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    item = scheduleRepo.save(item);
    log.info("Created deliveryScheduleId={} for projectId={}", item.getId(), projectId);
    return apiResponseAdaptor.success(
        "Delivery schedule item created successfully.", toResponse(item));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(
      Long projectId, Long itemId, DeliveryScheduleDto.UpdateRequest request) {
    ProjectDeliverySchedule item = requireItem(projectId, itemId);
    item.setMilestone(request.milestone().trim());
    item.setDeliverable(trimToNull(request.deliverable()));
    item.setPlannedDate(request.plannedDate());
    item.setActualDate(request.actualDate());
    item.setStatus(validateStatus(request.status()));
    item.setRemarks(trimToNull(request.remarks()));
    item.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    scheduleRepo.save(item);
    return apiResponseAdaptor.success(
        "Delivery schedule item updated successfully.", toResponse(item));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long projectId, Long itemId) {
    ProjectDeliverySchedule item = requireItem(projectId, itemId);
    scheduleRepo.delete(item);
    return apiResponseAdaptor.success("Delivery schedule item deleted successfully.");
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private ProjectDeliverySchedule requireItem(Long projectId, Long itemId) {
    return scheduleRepo
        .findByIdAndProject_Id(itemId, projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Delivery schedule item not found."));
  }

  private static String validateStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return "PENDING";
    }
    String value = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    if (!STATUSES.contains(value)) {
      throw new ApplicationException(
          "Status must be one of: PENDING, IN_PROGRESS, COMPLETED, DELAYED.");
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

  private DeliveryScheduleDto.Response toResponse(ProjectDeliverySchedule d) {
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
}
