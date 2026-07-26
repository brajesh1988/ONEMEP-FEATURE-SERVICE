package com.netlink.onemep_feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalAttachment;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidSpecificationRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalAttachmentRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalParameterRepo;
import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import com.netlink.onemep_feature.technical.repo.TechnicalMasterRepo;
import com.netlink.onemep_feature.unit.repo.UnitRepo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for Technical Master rules (ONEMEP-29): empty shell, param/DID + attachment guards.
 */
@ExtendWith(MockitoExtension.class)
class ProjectTechnicalMasterServiceImplTest {

  @Mock private ProjectTechnicalMasterRepo technicalMasterRepo;
  @Mock private ProjectTechnicalParameterRepo parameterRepo;
  @Mock private ProjectDidSpecificationRepo didRepo;
  @Mock private ProjectTechnicalAttachmentRepo attachmentRepo;
  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  @Mock private TechnicalMasterRepo technicalFieldRepo;
  @Mock private UnitRepo unitRepo;
  @Mock private ProjectActivityLogRepo activityRepo;

  private ProjectTechnicalMasterServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectTechnicalMasterServiceImpl(
            technicalMasterRepo,
            parameterRepo,
            didRepo,
            attachmentRepo,
            projectRepo,
            deliveryScheduleRepo,
            technicalFieldRepo,
            unitRepo,
            activityRepo,
            new ApiResponseAdaptor());
  }

  @Test
  void get_whenNoMaster_returnsEmptyShellWithClientInfo() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(deliveryScheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(1L)).thenReturn(List.of());

    ApiResponse<?> response = service.get(1L);

    TechnicalMasterDto.Response data = (TechnicalMasterDto.Response) response.getData();
    assertThat(data.exists()).isFalse();
    assertThat(data.projectId()).isEqualTo(1L);
    assertThat(data.commonParameters()).isEmpty();
    assertThat(data.clientInfo().client()).isEqualTo("Acme");
    assertThat(data.clientInfo().location()).isEqualTo("Dubai");
  }

  @Test
  void upsert_invalidScope_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    TechnicalMasterDto.UpsertRequest request =
        new TechnicalMasterDto.UpsertRequest(
            null,
            List.of(new TechnicalMasterDto.ParameterRequest("BOGUS", 5L, null, "1", null)),
            null);

    assertThatThrownBy(() -> service.upsert(1L, request)).isInstanceOf(ApplicationException.class);
    verify(parameterRepo, never()).save(any());
  }

  @Test
  void upsert_duplicateParameter_throwsDuplicate() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    lenient().when(technicalFieldRepo.findById(anyLong())).thenReturn(Optional.of(field(5L)));

    TechnicalMasterDto.ParameterRequest p =
        new TechnicalMasterDto.ParameterRequest("COMMON", 5L, null, "1", null);
    TechnicalMasterDto.UpsertRequest request =
        new TechnicalMasterDto.UpsertRequest(null, List.of(p, p), null);

    assertThatThrownBy(() -> service.upsert(1L, request))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void upsert_unknownTechnicalField_throwsNotFound() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(technicalFieldRepo.findById(99L)).thenReturn(Optional.empty());

    TechnicalMasterDto.UpsertRequest request =
        new TechnicalMasterDto.UpsertRequest(
            null,
            List.of(new TechnicalMasterDto.ParameterRequest("COMMON", 99L, null, "1", null)),
            null);

    assertThatThrownBy(() -> service.upsert(1L, request))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void uploadAttachment_disallowedExtension_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master()));
    MultipartFile file =
        new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[] {1});

    assertThatThrownBy(() -> service.uploadAttachment(1L, file))
        .isInstanceOf(ApplicationException.class);
    verify(attachmentRepo, never()).save(any());
  }

  @Test
  void uploadAttachment_overSizeLimit_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master()));
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("huge.pdf");
    when(file.getSize()).thenReturn(151L * 1024 * 1024);

    assertThatThrownBy(() -> service.uploadAttachment(1L, file))
        .isInstanceOf(ApplicationException.class);
    verify(attachmentRepo, never()).save(any());
  }

  @Test
  void downloadAttachment_missing_throwsNotFound() {
    when(attachmentRepo.findByIdAndTechnicalMaster_Project_Id(99L, 1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.downloadAttachment(1L, 99L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void deleteAttachment_scopedToProject_deletes() {
    ProjectTechnicalAttachment attachment = new ProjectTechnicalAttachment();
    attachment.setId(7L);
    attachment.setFileName("spec.pdf");
    attachment.setTechnicalMaster(master());
    when(attachmentRepo.findByIdAndTechnicalMaster_Project_Id(7L, 1L))
        .thenReturn(Optional.of(attachment));

    service.deleteAttachment(1L, 7L);

    verify(attachmentRepo).delete(attachment);
  }

  @Test
  void getSummary_whenPresent_returnsCountsAndVersion() {
    ProjectTechnicalMaster master = master();
    master.setVersion(3L);
    master.setRemarks("general inputs");
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master));
    when(parameterRepo.countByTechnicalMaster_IdAndScope(10L, "COMMON")).thenReturn(2L);
    when(parameterRepo.countByTechnicalMaster_IdAndScope(10L, "CATEGORY_SPECIFIC")).thenReturn(1L);
    when(didRepo.countByTechnicalMaster_Id(10L)).thenReturn(1L);
    when(attachmentRepo.countByTechnicalMaster_Id(10L)).thenReturn(3L);

    TechnicalMasterDto.Summary data = (TechnicalMasterDto.Summary) service.getSummary(1L).getData();

    assertThat(data.exists()).isTrue();
    assertThat(data.editable()).isTrue();
    assertThat(data.version()).isEqualTo(3L);
    assertThat(data.commonParameterCount()).isEqualTo(2L);
    assertThat(data.categorySpecificParameterCount()).isEqualTo(1L);
    assertThat(data.didSpecificationCount()).isEqualTo(1L);
    assertThat(data.attachmentCount()).isEqualTo(3L);
    assertThat(data.clientInfo().client()).isEqualTo("Acme");
  }

  @Test
  void getSummary_whenNoMaster_returnsEmptyShell() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());

    TechnicalMasterDto.Summary data = (TechnicalMasterDto.Summary) service.getSummary(1L).getData();

    assertThat(data.exists()).isFalse();
    assertThat(data.editable()).isFalse();
    assertThat(data.commonParameterCount()).isZero();
    assertThat(data.attachmentCount()).isZero();
    assertThat(data.clientInfo().location()).isEqualTo("Dubai");
  }

  @Test
  void getSummary_unknownProject_throwsNotFound() {
    when(projectRepo.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummary(2L)).isInstanceOf(ResourceNotFoundException.class);
  }

  private static ProjectTechnicalMaster withId(ProjectTechnicalMaster m) {
    m.setId(10L);
    return m;
  }

  private static ProjectTechnicalMaster master() {
    ProjectTechnicalMaster m = new ProjectTechnicalMaster();
    m.setId(10L);
    m.setProject(project());
    return m;
  }

  private static TechnicalMaster field(Long id) {
    TechnicalMaster f = new TechnicalMaster();
    f.setId(id);
    f.setName("Voltage");
    return f;
  }

  private static ProjectMaster project() {
    ProjectMaster p = new ProjectMaster();
    p.setId(1L);
    p.setName("Apollo");
    p.setClient("Acme");
    p.setLocation("Dubai");
    return p;
  }
}
