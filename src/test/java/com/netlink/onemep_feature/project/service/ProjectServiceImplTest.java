package com.netlink.onemep_feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.category.repo.CategoryRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.detailinglevel.repo.DetailingLevelRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.handlingoffice.repo.HandlingOfficeRepo;
import com.netlink.onemep_feature.notification.ProjectNotificationService;
import com.netlink.onemep_feature.project.dto.ProjectDto;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectActivityLogRepo;
import com.netlink.onemep_feature.project.repo.ProjectDeliveryScheduleRepo;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.project.repo.ProjectSpecSheetRepo;
import com.netlink.onemep_feature.project.repo.ProjectStakeholderRepo;
import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import com.netlink.onemep_feature.teamrole.repo.TeamRoleRepo;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Business-logic unit tests for the project aggregate (ONEMEP-12/13/14/15): Type-driven Project ID
 * generation, protected fields on edit, user-existence validation, lifecycle reason enforcement,
 * structured notifications, confirm flow, and structured-filter validation.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectMemberMappingRepo memberRepo;
  @Mock private ProjectActivityLogRepo activityRepo;
  @Mock private ProjectSpecSheetRepo specSheetRepo;
  @Mock private ProjectDeliveryScheduleRepo deliveryScheduleRepo;
  @Mock private ProjectStakeholderRepo stakeholderRepo;
  @Mock private CategoryRepo categoryRepo;
  @Mock private TeamRoleRepo teamRoleRepo;
  @Mock private HandlingOfficeRepo handlingOfficeRepo;
  @Mock private DetailingLevelRepo detailingLevelRepo;
  @Mock private UserDirectoryClient userDirectory;
  @Mock private ProjectNotificationService notificationService;

  private ProjectServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectServiceImpl(
            projectRepo,
            memberRepo,
            activityRepo,
            specSheetRepo,
            deliveryScheduleRepo,
            stakeholderRepo,
            categoryRepo,
            teamRoleRepo,
            handlingOfficeRepo,
            detailingLevelRepo,
            userDirectory,
            notificationService,
            new ApiResponseAdaptor());
  }

  @Test
  void create_nonConfirmed_generatesNcNumber_defaultsLifecycleActive() {
    CategoryMaster category = category(10L, "INF", "Infrastructure", 6);
    when(projectRepo.findByNameIgnoreCase("Apollo")).thenReturn(Optional.empty());
    when(categoryRepo.findById(10L)).thenReturn(Optional.of(category));
    when(projectRepo.saveAndFlush(any(ProjectMaster.class)))
        .thenAnswer(
            inv -> {
              ProjectMaster p = inv.getArgument(0);
              p.setId(100L);
              return p;
            });
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userDirectory.findMissing(any())).thenReturn(Set.of());
    when(teamRoleRepo.findById(5L)).thenReturn(Optional.of(teamRole(5L)));
    when(teamRoleRepo.findById(7L)).thenReturn(Optional.of(teamRole(7L)));
    // Leads are derived at read time from members carrying the "Project Lead" role.
    when(memberRepo.findByProject_Id(100L))
        .thenReturn(
            List.of(
                memberMapping(null, 1L, "Project Lead"),
                memberMapping(null, 3L, "Project Lead"),
                memberMapping(null, 2L, "Draughtsman")));

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest(
            "Apollo",
            10L,
            "NON_CONFIRMED",
            "HIGH",
            null,
            "Acme Corp",
            "Dubai",
            null,
            null,
            "demo",
            List.of(
                new ProjectDto.MemberRequest(1L, 5L),
                new ProjectDto.MemberRequest(3L, 5L),
                new ProjectDto.MemberRequest(2L, 7L)));

    ProjectDto.Detail data = (ProjectDto.Detail) service.create(request).getData();

    assertThat(data.projectNumber()).isEqualTo("NC0100");
    assertThat(data.type()).isEqualTo("NON_CONFIRMED");
    assertThat(data.typeLocked()).isFalse();
    assertThat(data.lifecycleStatus()).isEqualTo("ACTIVE");
    assertThat(data.priority()).isEqualTo("HIGH");
    assertThat(data.client()).isEqualTo("Acme Corp");
    assertThat(data.leadUserIds()).containsExactly(1L, 3L);
    verify(memberRepo).flush();
  }

  @Test
  void create_confirmed_generatesSeriesNumber_andLocksType() {
    CategoryMaster category = category(10L, "HTL", "Hotel", 4);
    when(projectRepo.findByNameIgnoreCase("Marina")).thenReturn(Optional.empty());
    when(categoryRepo.findById(10L)).thenReturn(Optional.of(category));
    when(projectRepo.saveAndFlush(any(ProjectMaster.class)))
        .thenAnswer(
            inv -> {
              ProjectMaster p = inv.getArgument(0);
              p.setId(12L);
              return p;
            });
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberRepo.findByProject_Id(12L)).thenReturn(List.of());

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest(
            "Marina", 10L, "CONFIRMED", "MEDIUM", "ACTIVE", null, null, null, null, null, null);

    ProjectDto.Detail data = (ProjectDto.Detail) service.create(request).getData();

    assertThat(data.projectNumber()).isEqualTo("40012");
    assertThat(data.type()).isEqualTo("CONFIRMED");
    assertThat(data.typeLocked()).isTrue();
  }

  @Test
  void create_confirmedWithoutSeriesCode_throws() {
    CategoryMaster category = category(10L, "INF", "Infrastructure", null);
    when(projectRepo.findByNameIgnoreCase("NoSeries")).thenReturn(Optional.empty());
    when(categoryRepo.findById(10L)).thenReturn(Optional.of(category));

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest(
            "NoSeries", 10L, "CONFIRMED", "LOW", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.create(request)).isInstanceOf(ApplicationException.class);
    verify(projectRepo, never()).saveAndFlush(any());
  }

  @Test
  void create_invalidMemberUserId_throwsNotFound() {
    CategoryMaster category = category(10L, "INF", "Infrastructure", 6);
    when(projectRepo.findByNameIgnoreCase("BadMember")).thenReturn(Optional.empty());
    when(categoryRepo.findById(10L)).thenReturn(Optional.of(category));
    when(projectRepo.saveAndFlush(any(ProjectMaster.class)))
        .thenAnswer(
            inv -> {
              ProjectMaster p = inv.getArgument(0);
              p.setId(101L);
              return p;
            });
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userDirectory.findMissing(any())).thenReturn(Set.of(999999L));

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest(
            "BadMember",
            10L,
            "NON_CONFIRMED",
            "LOW",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new ProjectDto.MemberRequest(999999L, 5L)));

    assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void update_keepsNumberAndCategoryLocked_andNotifiesOnLifecycleAndPriorityChange() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.findByNameIgnoreCaseAndIdNot("Apollo II", 1L)).thenReturn(Optional.empty());
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberRepo.findByProject_Id(1L)).thenReturn(List.of());

    ProjectDto.UpdateRequest request =
        new ProjectDto.UpdateRequest(
            "Apollo II", "CRITICAL", "COMPLETED", null, null, null, null, null, null, "upd", null);

    ProjectDto.Detail data = (ProjectDto.Detail) service.update(1L, request).getData();

    assertThat(data.projectNumber()).isEqualTo("NC0001");
    assertThat(data.categoryId()).isEqualTo(10L);
    assertThat(data.priority()).isEqualTo("CRITICAL");
    assertThat(data.lifecycleStatus()).isEqualTo("COMPLETED");
    verify(notificationService)
        .notifyLifecycleChanged(
            eq(existing), eq("ACTIVE"), eq("COMPLETED"), any(), any(), anyList());
    verify(notificationService)
        .notifyPriorityChanged(eq(existing), eq("MEDIUM"), eq("CRITICAL"), any(), anyList());
  }

  @Test
  void update_toOnHoldWithoutReason_throws() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.findByNameIgnoreCaseAndIdNot("Apollo", 1L)).thenReturn(Optional.empty());

    ProjectDto.UpdateRequest request =
        new ProjectDto.UpdateRequest(
            "Apollo", "MEDIUM", "ON_HOLD", null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.update(1L, request)).isInstanceOf(ApplicationException.class);
  }

  @Test
  void updateLifecycle_toClosedWithoutReason_throws() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.updateLifecycle(1L, "CLOSED", " "))
        .isInstanceOf(ApplicationException.class);
    verify(notificationService, never())
        .notifyLifecycleChanged(any(), any(), any(), any(), any(), anyList());
  }

  @Test
  void updateType_confirmsNonConfirmed_reassignsNumberAndLocks() {
    ProjectMaster existing = existingProject();
    existing.setType("NON_CONFIRMED");
    existing.setTypeLocked(false);
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberRepo.findByProject_Id(1L)).thenReturn(List.of());

    ProjectDto.Detail data = (ProjectDto.Detail) service.updateType(1L, "CONFIRMED").getData();

    assertThat(data.type()).isEqualTo("CONFIRMED");
    assertThat(data.typeLocked()).isTrue();
    assertThat(data.projectNumber()).isEqualTo("60001"); // series 6 + id 0001
  }

  @Test
  void updateType_confirmedCannotRevertToNonConfirmed() {
    ProjectMaster existing = existingProject();
    existing.setType("CONFIRMED");
    existing.setTypeLocked(true);
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.updateType(1L, "NON_CONFIRMED"))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void updatePriority_changesValueAndNotifies() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberRepo.findByProject_Id(1L))
        .thenReturn(List.of(memberMapping(null, 1L, "Project Lead")));

    ApiResponse<?> response = service.updatePriority(1L, "high");

    assertThat(response.getMessage()).contains("priority");
    assertThat(existing.getPriority()).isEqualTo("HIGH");
    verify(notificationService)
        .notifyPriorityChanged(eq(existing), eq("MEDIUM"), eq("HIGH"), any(), anyList());
  }

  @Test
  void updateLifecycle_sameStatus_doesNotNotify() {
    ProjectMaster existing = existingProject();
    existing.setLifecycleStatus("ACTIVE");
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));

    service.updateLifecycle(1L, "active", null);

    verify(notificationService, never())
        .notifyLifecycleChanged(any(), any(), any(), any(), any(), anyList());
  }

  @Test
  void updatePriority_invalidValue_throwsApplicationException() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existingProject()));

    assertThatThrownBy(() -> service.updatePriority(1L, "URGENT"))
        .isInstanceOf(ApplicationException.class);
    verify(notificationService, never())
        .notifyPriorityChanged(any(), any(), any(), any(), anyList());
  }

  @Test
  void list_populatesLeadUsersFromProjectLeadMembers() {
    ProjectMaster project = existingProject();
    when(projectRepo.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(project)));
    when(memberRepo.findByProject_IdIn(List.of(1L)))
        .thenReturn(
            List.of(
                memberMapping(project, 1L, "Project Lead"),
                memberMapping(project, 3L, "project lead"), // case-insensitive match
                memberMapping(project, 2L, "Draughtsman"))); // non-lead, excluded
    when(userDirectory.resolve(any()))
        .thenReturn(
            Map.of(
                1L, new UserSummary(1L, "Ava Lead", "ava@onemep.com"),
                3L, new UserSummary(3L, "Cara Lead", "cara@onemep.com")));

    ApiResponse<?> response = service.list(new GenericListRequestDTO());
    PageResponse<ProjectDto.ListItem> page = (PageResponse<ProjectDto.ListItem>) response.getData();
    ProjectDto.ListItem item = page.getContent().get(0);

    assertThat(item.leadUsers())
        .extracting(ProjectDto.UserRef::userId, ProjectDto.UserRef::userName)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(1L, "Ava Lead"),
            org.assertj.core.groups.Tuple.tuple(3L, "Cara Lead"));
  }

  @Test
  void list_badPriorityFilter_throwsApplicationException() {
    GenericListRequestDTO request = new GenericListRequestDTO();
    request.setFilters(Map.of("priority", "WRONG"));

    assertThatThrownBy(() -> service.list(request)).isInstanceOf(ApplicationException.class);
    verify(projectRepo, never())
        .findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Pageable.class));
  }

  // ---------------------------------------------------------------- helpers

  private ProjectMaster existingProject() {
    ProjectMaster p = new ProjectMaster();
    p.setId(1L);
    p.setProjectNumber("NC0001");
    p.setName("Apollo");
    p.setCategory(category(10L, "INF", "Infrastructure", 6));
    p.setType("NON_CONFIRMED");
    p.setTypeLocked(false);
    p.setPriority("MEDIUM");
    p.setLifecycleStatus("ACTIVE");
    p.setActive(true);
    return p;
  }

  private static CategoryMaster category(long id, String prefix, String name, Integer seriesCode) {
    CategoryMaster c = new CategoryMaster();
    c.setId(id);
    c.setPrefix(prefix);
    c.setName(name);
    c.setSeriesCode(seriesCode);
    c.setActive(true);
    return c;
  }

  private static TeamRoleMaster teamRole(long id) {
    TeamRoleMaster r = new TeamRoleMaster();
    r.setId(id);
    r.setName("Role " + id);
    TierMaster tier = new TierMaster();
    tier.setId(1L);
    tier.setName("Tier 1");
    r.setTier(tier);
    return r;
  }

  private static ProjectMemberMapping memberMapping(
      ProjectMaster project, long userId, String roleName) {
    ProjectMemberMapping m = new ProjectMemberMapping();
    m.setProject(project);
    m.setUserId(userId);
    TeamRoleMaster role = new TeamRoleMaster();
    role.setId(userId);
    role.setName(roleName);
    m.setTeamRole(role);
    return m;
  }
}
