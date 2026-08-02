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
import com.netlink.onemep_feature.project.config.DidDefaultsProperties;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ArchitectTeam;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ClientInformation;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.ContactRow;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DeliveryStage;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.DesignIntentBrief;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.Response;
import com.netlink.onemep_feature.project.dto.DidSpecificationDto.StructureConsultantTeam;
import com.netlink.onemep_feature.project.model.DidGreenRatingOption;
import com.netlink.onemep_feature.project.model.DidPartyType;
import com.netlink.onemep_feature.project.model.ProjectDidClientInfo;
import com.netlink.onemep_feature.project.model.ProjectDidContact;
import com.netlink.onemep_feature.project.model.ProjectDidSpecification;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import com.netlink.onemep_feature.project.repo.DidGreenRatingOptionRepo;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidArchitectTeamRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidClientInfoRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidContactRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidSpecificationRepo;
import com.netlink.onemep_feature.project.repo.ProjectDidStructureConsultantTeamRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectTechnicalMasterRepo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for the DID tab (Technical Master → DID). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectDidSpecificationServiceImplTest {

  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectTechnicalMasterRepo technicalMasterRepo;
  @Mock private ProjectDidSpecificationRepo didSpecRepo;
  @Mock private DidGreenRatingOptionRepo greenRatingOptionRepo;
  @Mock private ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  @Mock private ProjectDidClientInfoRepo clientInfoRepo;
  @Mock private ProjectDidArchitectTeamRepo architectTeamRepo;
  @Mock private ProjectDidStructureConsultantTeamRepo structureTeamRepo;
  @Mock private ProjectDidContactRepo contactRepo;
  @Mock private ProjectActivityLogRepo activityRepo;

  private ProjectDidSpecificationServiceImpl service;

  @BeforeEach
  void setUp() {
    DidDefaultsProperties didDefaults =
        new DidDefaultsProperties(
            List.of("Stage A", "Stage B"),
            List.of("Project Owner", "Project Head", "Project Coordinator"));
    service =
        new ProjectDidSpecificationServiceImpl(
            projectRepo,
            technicalMasterRepo,
            didSpecRepo,
            greenRatingOptionRepo,
            deliveryScheduleRepo,
            clientInfoRepo,
            architectTeamRepo,
            structureTeamRepo,
            contactRepo,
            activityRepo,
            didDefaults,
            new ApiResponseAdaptor());
    when(projectRepo.findById(1L)).thenReturn(Optional.of(project()));
  }

  @Test
  void upsert_withValidPayload_savesAllSubsections() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(greenRatingOptionRepo.findByCodeAndActiveTrue("IGBC"))
        .thenReturn(Optional.of(greenRatingOption("IGBC")));
    when(deliveryScheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(contactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(
                new DesignIntentBrief("Locked intent", "RFI response", "IGBC", "Solar"))
            .deliveryStage(
                new DeliveryStage(
                    null, "Stage 1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))
            .clientInformation(
                "Acme",
                "Acme Co",
                List.of(
                    new ContactRow(null, "Project Owner", null, null, null, true),
                    new ContactRow(
                        null, "Site Lead", "Jane Doe", "jane@acme.com", "+91 9876543210", false)))
            .architectTeam("ArchCo", List.of())
            .structureConsultantTeam("StructCo", List.of());

    service.upsert(1L, request.build());

    verify(didSpecRepo).save(any(ProjectDidSpecification.class));
    verify(deliveryScheduleRepo, times(1)).save(any());
    verify(clientInfoRepo).save(any(ProjectDidClientInfo.class));
    verify(contactRepo, times(2)).save(any(ProjectDidContact.class));
    verify(architectTeamRepo).save(any());
    verify(structureTeamRepo).save(any());
    verify(activityRepo).save(any());
  }

  @Test
  void upsert_withBlankLockedDesignIntent_throws() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(new DesignIntentBrief("   ", null, null, null));

    assertThatThrownBy(() -> service.upsert(1L, request.build()))
        .isInstanceOf(ApplicationException.class);
    verify(didSpecRepo, never()).save(any());
  }

  @Test
  void upsert_withInvalidGreenRatingTarget_throws() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(
                new DesignIntentBrief("Locked intent", null, "NOT_A_REAL_CODE", null));

    assertThatThrownBy(() -> service.upsert(1L, request.build()))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void upsert_withEndDateBeforeStartDate_throws() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(new DesignIntentBrief("Locked intent", null, null, null))
            .deliveryStage(
                new DeliveryStage(
                    null, "Stage 1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));

    assertThatThrownBy(() -> service.upsert(1L, request.build()))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void upsert_blankTrailingDeliveryRow_isSilentlyDropped() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());
    when(technicalMasterRepo.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    when(deliveryScheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(new DesignIntentBrief("Locked intent", null, null, null))
            .deliveryStage(
                new DeliveryStage(
                    null, "Stage 1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))
            .deliveryStage(new DeliveryStage(null, null, null, null));

    service.upsert(1L, request.build());

    verify(deliveryScheduleRepo, times(1)).save(any());
  }

  @Test
  void upsert_removingDefaultClientContact_throws() {
    ProjectTechnicalMaster master = master();
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master));
    when(technicalMasterRepo.saveAndFlush(any())).thenReturn(master);
    ProjectDidContact existingDefault = new ProjectDidContact();
    existingDefault.setId(5L);
    existingDefault.setPartyType(DidPartyType.CLIENT);
    existingDefault.setDesignation("Project Owner");
    existingDefault.setDefaultRow(true);
    when(contactRepo.findByTechnicalMaster_IdAndPartyTypeOrderByContactOrderAscIdAsc(
            10L, DidPartyType.CLIENT))
        .thenReturn(List.of(existingDefault));

    UpsertRequestBuilder request =
        new UpsertRequestBuilder()
            .designIntentBrief(new DesignIntentBrief("Locked intent", null, null, null))
            .clientInformation("Acme", null, List.of());

    assertThatThrownBy(() -> service.upsert(1L, request.build()))
        .isInstanceOf(ApplicationException.class);
    verify(contactRepo, never()).delete(existingDefault);
  }

  @Test
  void get_withNoExistingData_returnsDefaultsAndExistsFalse() {
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.empty());

    Response response = (Response) service.get(1L).getData();

    assertThat(response.exists()).isFalse();
    assertThat(response.designIntentBrief()).isNull();
    assertThat(response.deliverySchedule())
        .extracting(DeliveryStage::stageName)
        .containsExactly("Stage A", "Stage B");
    assertThat(response.deliverySchedule()).allMatch(s -> s.id() == null);
    assertThat(response.clientInformation().contacts())
        .extracting(ContactRow::designation)
        .containsExactly("Project Owner", "Project Head", "Project Coordinator");
    assertThat(response.architectTeam().contacts()).isEmpty();
  }

  @Test
  void get_withExistingData_returnsPersistedRowsNotDefaults() {
    ProjectTechnicalMaster master = master();
    when(technicalMasterRepo.findByProject_Id(1L)).thenReturn(Optional.of(master));

    ProjectDidSpecification spec = new ProjectDidSpecification();
    spec.setLockedDesignIntent("Existing intent");
    when(didSpecRepo.findByTechnicalMaster_Id(10L)).thenReturn(Optional.of(spec));

    ProjectDidClientInfo clientInfo = new ProjectDidClientInfo();
    clientInfo.setClientName("Acme");
    when(clientInfoRepo.findByTechnicalMaster_Id(10L)).thenReturn(Optional.of(clientInfo));

    ProjectDidContact contact = new ProjectDidContact();
    contact.setId(5L);
    contact.setDesignation("Project Owner");
    contact.setDefaultRow(true);
    when(contactRepo.findByTechnicalMaster_IdAndPartyTypeOrderByContactOrderAscIdAsc(
            10L, DidPartyType.CLIENT))
        .thenReturn(List.of(contact));

    Response response = (Response) service.get(1L).getData();

    assertThat(response.exists()).isTrue();
    assertThat(response.designIntentBrief().lockedDesignIntent()).isEqualTo("Existing intent");
    assertThat(response.clientInformation().clientName()).isEqualTo("Acme");
    assertThat(response.clientInformation().contacts()).hasSize(1);
    assertThat(response.deliverySchedule())
        .extracting(DeliveryStage::stageName)
        .containsExactly("Stage A", "Stage B"); // no persisted DID stages -> defaults still shown
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

  private static DidGreenRatingOption greenRatingOption(String code) {
    DidGreenRatingOption o = new DidGreenRatingOption();
    o.setCode(code);
    o.setLabel(code);
    return o;
  }

  private static ProjectMaster project() {
    CategoryMaster cat = new CategoryMaster();
    cat.setId(1L);
    cat.setSeriesCode(1);
    ProjectMaster p = new ProjectMaster();
    p.setId(1L);
    p.setName("Apollo");
    p.setClient("Acme");
    p.setLocation("Dubai");
    p.setCategory(cat);
    return p;
  }

  /** Small builder to keep the 5-subsection {@code UpsertRequest} readable in tests. */
  private static final class UpsertRequestBuilder {
    private DesignIntentBrief designIntentBrief;
    private final java.util.ArrayList<DeliveryStage> deliverySchedule = new java.util.ArrayList<>();
    private ClientInformation clientInformation = new ClientInformation(null, null, List.of());
    private ArchitectTeam architectTeam = new ArchitectTeam(null, List.of());
    private StructureConsultantTeam structureConsultantTeam =
        new StructureConsultantTeam(null, List.of());

    UpsertRequestBuilder designIntentBrief(DesignIntentBrief v) {
      this.designIntentBrief = v;
      return this;
    }

    UpsertRequestBuilder deliveryStage(DeliveryStage v) {
      this.deliverySchedule.add(v);
      return this;
    }

    UpsertRequestBuilder clientInformation(String name, String company, List<ContactRow> contacts) {
      this.clientInformation = new ClientInformation(name, company, contacts);
      return this;
    }

    UpsertRequestBuilder architectTeam(String firm, List<ContactRow> contacts) {
      this.architectTeam = new ArchitectTeam(firm, contacts);
      return this;
    }

    UpsertRequestBuilder structureConsultantTeam(String consultancy, List<ContactRow> contacts) {
      this.structureConsultantTeam = new StructureConsultantTeam(consultancy, contacts);
      return this;
    }

    DidSpecificationDto.UpsertRequest build() {
      return new DidSpecificationDto.UpsertRequest(
          designIntentBrief,
          deliverySchedule,
          clientInformation,
          architectTeam,
          structureConsultantTeam);
    }
  }
}
