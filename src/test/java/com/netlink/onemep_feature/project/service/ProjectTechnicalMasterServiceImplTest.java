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
import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for the editable, category-driven Technical Master (ONEMEP-29). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectTechnicalMasterServiceImplTest {

  @Mock private ProjectTechnicalMasterRepo technicalMasterRepo;
  @Mock private ProjectTechnicalFieldValueRepo fieldValueRepo;
  @Mock private TmSectionRepo sectionRepo;
  @Mock private TmFieldRepo fieldRepo;
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
            sectionRepo,
            fieldRepo,
            attachmentRepo,
            projectRepo,
            deliveryScheduleRepo,
            activityRepo,
            new ApiResponseAdaptor());
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project(1)));
    when(deliveryScheduleRepo.findByProject_IdOrderByPlannedDateAscIdAsc(1L)).thenReturn(List.of());
    when(fieldValueRepo.findByTechnicalMaster_Id(any())).thenReturn(List.of());
    when(attachmentRepo.findMetadataByProjectId(1L)).thenReturn(List.of());
  }

  @Test
  void getTemplate_buildsSectionsWithFields() {
    TmSection s = section(5L, 1, "HVAC", 1);
    when(sectionRepo.findBySeriesCodeOrderBySectionOrderAsc(1)).thenReturn(List.of(s));
    when(fieldRepo.findBySection_IdOrderByFieldOrderAsc(5L))
        .thenReturn(List.of(field(9L, s, 1, "Cooling load basis", "hvac__clb", true, true)));

    TechnicalMasterDto.Template t = (TechnicalMasterDto.Template) service.getTemplate(1L).getData();

    assertThat(t.seriesCode()).isEqualTo(1);
    assertThat(t.sections()).hasSize(1);
    assertThat(t.sections().get(0).id()).isEqualTo(5L);
    assertThat(t.sections().get(0).fields().get(0).required()).isTrue();
  }

  @Test
  void upsert_missingRequiredField_isBlocked() {
    TmSection s = section(5L, 1, "Site", 1);
    when(fieldRepo.findBySeriesCode(1))
        .thenReturn(
            List.of(
                field(9L, s, 1, "Plot area", "site__plot", true, true),
                field(10L, s, 1, "Remarks", "site__rem", false, true)));

    TechnicalMasterDto.UpsertRequest req =
        new TechnicalMasterDto.UpsertRequest(null, Map.of("site__rem", "x"));

    assertThatThrownBy(() -> service.upsert(1L, req)).isInstanceOf(ApplicationException.class);
    verify(fieldValueRepo, never()).save(any());
  }

  @Test
  void upsert_savesWhenRequiredFilled() {
    TmSection s = section(5L, 1, "Site", 1);
    when(fieldRepo.findBySeriesCode(1))
        .thenReturn(List.of(field(9L, s, 1, "Plot area", "site__plot", true, true)));
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    service.upsert(1L, new TechnicalMasterDto.UpsertRequest("n", Map.of("site__plot", "1000")));

    verify(fieldValueRepo).deleteByTechnicalMaster_Id(10L);
    verify(fieldValueRepo, times(1)).save(any(ProjectTechnicalFieldValue.class));
  }

  @Test
  void upsert_unknownOrInactiveKey_throws() {
    when(fieldRepo.findBySeriesCode(1)).thenReturn(List.of());

    assertThatThrownBy(
            () -> service.upsert(1L, new TechnicalMasterDto.UpsertRequest(null, Map.of("x", "y"))))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void createField_persistsAndReturnsTemplate() {
    TmSection s = section(5L, 1, "HVAC", 1);
    when(sectionRepo.findById(5L)).thenReturn(Optional.of(s));
    when(fieldRepo.existsBySeriesCodeAndFieldKey(any(), any())).thenReturn(false);
    when(fieldRepo.countBySection_Id(5L)).thenReturn(0L);
    when(sectionRepo.findBySeriesCodeOrderBySectionOrderAsc(1)).thenReturn(List.of(s));
    when(fieldRepo.findBySection_IdOrderByFieldOrderAsc(5L)).thenReturn(List.of());

    service.createField(
        1L, new TechnicalMasterDto.FieldRequest(5L, "New load", "kVA", "NUMBER", true, true));

    verify(fieldRepo).save(any(TmField.class));
  }

  @Test
  void createSection_persists() {
    when(sectionRepo.countBySeriesCode(1)).thenReturn(3L);
    when(sectionRepo.findBySeriesCodeOrderBySectionOrderAsc(1)).thenReturn(List.of());

    service.createSection(1L, new TechnicalMasterDto.SectionRequest("New head", true));

    verify(sectionRepo).save(any(TmSection.class));
  }

  @Test
  void uploadAttachment_disallowedExtension_throws() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master()));
    MultipartFile file =
        new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {1});
    assertThatThrownBy(() -> service.uploadAttachment(1L, file))
        .isInstanceOf(ApplicationException.class);
    verify(attachmentRepo, never()).save(any());
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

  private static TmSection section(Long id, int series, String title, int order) {
    TmSection s = new TmSection();
    s.setId(id);
    s.setSeriesCode(series);
    s.setTitle(title);
    s.setSectionOrder(order);
    s.setActive(true);
    return s;
  }

  private static TmField field(
      Long id,
      TmSection s,
      int series,
      String label,
      String key,
      boolean required,
      boolean active) {
    TmField f = new TmField();
    f.setId(id);
    f.setSection(s);
    f.setSeriesCode(series);
    f.setLabel(label);
    f.setFieldKey(key);
    f.setDataType("TEXT");
    f.setRequired(required);
    f.setActive(active);
    f.setFeeds("REF");
    f.setFieldOrder(1);
    return f;
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
