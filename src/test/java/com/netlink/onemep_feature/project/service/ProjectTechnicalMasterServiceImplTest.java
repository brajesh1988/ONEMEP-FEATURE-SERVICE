package com.netlink.onemep_feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for the category-driven Technical Master (ONEMEP-29): template, values, guards. */
@ExtendWith(MockitoExtension.class)
class ProjectTechnicalMasterServiceImplTest {

  @Mock private ProjectTechnicalMasterRepo technicalMasterRepo;
  @Mock private ProjectTechnicalFieldValueRepo fieldValueRepo;
  @Mock private TmFieldRepo tmFieldRepo;
  @Mock private ProjectTechnicalAttachmentRepo attachmentRepo;
  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  @Mock private ProjectActivityLogRepo activityRepo;

  private ProjectTechnicalMasterServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectTechnicalMasterServiceImpl(
            technicalMasterRepo,
            fieldValueRepo,
            tmFieldRepo,
            attachmentRepo,
            projectRepo,
            deliveryScheduleRepo,
            activityRepo,
            new ApiResponseAdaptor());
  }

  @Test
  void getTemplate_groupsFieldsBySection() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(tmFieldRepo.findByCategorySeries(1))
        .thenReturn(
            List.of(
                tmField("Site & area statement", 2, "Plot area", "site__plot", "NUMBER", true),
                tmField("Site & area statement", 2, "GFA", "site__gfa", "NUMBER", true),
                tmField("HVAC", 3, "Fresh air rate", "hvac__far", "NUMBER", false)));

    TechnicalMasterDto.Template t = (TechnicalMasterDto.Template) service.getTemplate(1L).getData();

    assertThat(t.seriesCode()).isEqualTo(1);
    assertThat(t.sections()).hasSize(2);
    assertThat(t.sections().get(0).title()).isEqualTo("Site & area statement");
    assertThat(t.sections().get(0).fields()).hasSize(2);
    assertThat(t.sections().get(0).fields().get(0).required()).isTrue();
    assertThat(t.sections().get(1).title()).isEqualTo("HVAC");
  }

  @Test
  void get_whenNoMaster_returnsEmptyShellWithClientInfo() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(deliveryScheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(1L)).thenReturn(List.of());

    TechnicalMasterDto.Response d = (TechnicalMasterDto.Response) service.get(1L).getData();

    assertThat(d.exists()).isFalse();
    assertThat(d.values()).isEmpty();
    assertThat(d.clientInfo().client()).isEqualTo("Acme");
  }

  @Test
  void upsert_savesOnlyNonBlankKnownValues() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(tmFieldRepo.findByCategorySeries(1))
        .thenReturn(
            List.of(
                tmField("Site & area statement", 2, "Plot area", "site__plot", "NUMBER", true),
                tmField("Site & area statement", 2, "GFA", "site__gfa", "NUMBER", true)));

    TechnicalMasterDto.UpsertRequest req =
        new TechnicalMasterDto.UpsertRequest(
            "notes", Map.of("site__plot", "1000", "site__gfa", "  "));

    service.upsert(1L, req);

    verify(fieldValueRepo).deleteByTechnicalMaster_Id(10L);
    // only the non-blank "site__plot" is persisted; the blank "site__gfa" is skipped
    verify(fieldValueRepo, times(1)).save(any(ProjectTechnicalFieldValue.class));
  }

  @Test
  void upsert_unknownFieldKey_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(tmFieldRepo.findByCategorySeries(1))
        .thenReturn(
            List.of(
                tmField("Site & area statement", 2, "Plot area", "site__plot", "NUMBER", true)));

    TechnicalMasterDto.UpsertRequest req =
        new TechnicalMasterDto.UpsertRequest(null, Map.of("bogus__key", "x"));

    assertThatThrownBy(() -> service.upsert(1L, req)).isInstanceOf(ApplicationException.class);
    verify(fieldValueRepo, never()).save(any());
  }

  @Test
  void getSummary_present_countsFieldsAndSections() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(tmFieldRepo.findByCategorySeries(1))
        .thenReturn(
            List.of(
                tmField("Site & area statement", 2, "Plot area", "site__plot", "NUMBER", true),
                tmField("HVAC", 3, "Fresh air rate", "hvac__far", "NUMBER", false),
                tmField("HVAC", 3, "System type", "hvac__sys", "TEXT", false)));
    ProjectTechnicalMaster master = master();
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master));
    ProjectTechnicalFieldValue v = new ProjectTechnicalFieldValue();
    v.setFieldKey("site__plot");
    v.setValue("1000");
    when(fieldValueRepo.findByTechnicalMaster_Id(10L)).thenReturn(List.of(v));
    when(attachmentRepo.countByTechnicalMaster_Id(10L)).thenReturn(2L);

    TechnicalMasterDto.Summary s = (TechnicalMasterDto.Summary) service.getSummary(1L).getData();

    assertThat(s.exists()).isTrue();
    assertThat(s.totalFields()).isEqualTo(3L);
    assertThat(s.sectionCount()).isEqualTo(2L);
    assertThat(s.filledFieldCount()).isEqualTo(1L);
    assertThat(s.attachmentCount()).isEqualTo(2L);
  }

  @Test
  void uploadAttachment_disallowedExtension_throws() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master()));
    MultipartFile file =
        new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[] {1});

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

  private static TmField tmField(
      String section, int secOrder, String label, String key, String dtype, boolean core) {
    TmField f = new TmField();
    f.setSection(section);
    f.setSectionOrder(secOrder);
    f.setLabel(label);
    f.setFieldKey(key);
    f.setDataType(dtype);
    f.setFeeds("REF");
    f.setCore(core);
    f.setFieldOrder(1);
    return f;
  }

  private static ProjectTechnicalMaster withId(ProjectTechnicalMaster m) {
    m.setId(10L);
    return m;
  }

  private static ProjectTechnicalMaster master() {
    ProjectTechnicalMaster m = new ProjectTechnicalMaster();
    m.setId(10L);
    m.setProject(project(1));
    return m;
  }

  private static ProjectMaster project(int seriesCode) {
    CategoryMaster cat = new CategoryMaster();
    cat.setId(1L);
    cat.setSeriesCode(seriesCode);
    ProjectMaster p = new ProjectMaster();
    p.setId(1L);
    p.setName("Apollo");
    p.setClient("Acme");
    p.setLocation("Dubai");
    p.setCategory(cat);
    return p;
  }
}
