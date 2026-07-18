package com.netlink.onemep_feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import com.netlink.onemep_feature.category.repo.CategoryRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.notification.ProjectNotificationService;
import com.netlink.onemep_feature.project.dto.ProjectDto;
import com.netlink.onemep_feature.project.model.ProjectLeadMapping;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.repo.ProjectLeadMappingRepo;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import com.netlink.onemep_feature.teamrole.repo.TeamRoleRepo;
import com.netlink.onemep_feature.tier.model.TierMaster;
import com.netlink.onemep_feature.user.model.UserAccountRef;
import com.netlink.onemep_feature.user.repo.UserAccountRefRepo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Business-logic unit tests for the project aggregate (ONEMEP-12/13/14/15): number generation,
 * protected fields on edit, user-existence validation, lifecycle/priority notifications, and
 * structured-filter validation.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

  @Mock private ProjectRepo projectRepo;
  @Mock private ProjectLeadMappingRepo leadRepo;
  @Mock private ProjectMemberMappingRepo memberRepo;
  @Mock private CategoryRepo categoryRepo;
  @Mock private TeamRoleRepo teamRoleRepo;
  @Mock private UserAccountRefRepo userRepo;
  @Mock private ProjectNotificationService notificationService;

  private ProjectServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectServiceImpl(
            projectRepo,
            leadRepo,
            memberRepo,
            categoryRepo,
            teamRoleRepo,
            userRepo,
            notificationService,
            new ApiResponseAdaptor());
  }

  @Test
  void create_generatesProjectNumberFromPrefix_defaultsLifecycleDraft() {
    CategoryMaster category = category(10L, "INF", "Infrastructure");
    List<UserAccountRef> refs = List.of(userRef(1L), userRef(2L), userRef(3L));
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
    when(userRepo.findByIdIn(any())).thenReturn(refs);
    when(teamRoleRepo.findById(7L)).thenReturn(Optional.of(teamRole(7L)));
    when(leadRepo.findByProject_Id(100L)).thenReturn(List.of(leadMapping(1L), leadMapping(3L)));
    when(memberRepo.findByProject_Id(100L)).thenReturn(List.of());

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest(
            "Apollo",
            10L,
            "HIGH",
            "demo",
            List.of(1L, 3L),
            List.of(new ProjectDto.MemberRequest(2L, 7L)));

    ApiResponse<?> response = service.create(request);

    ProjectDto.Detail data = (ProjectDto.Detail) response.getData();
    assertThat(data.projectNumber()).isEqualTo("INF-00100");
    assertThat(data.lifecycleStatus()).isEqualTo("DRAFT");
    assertThat(data.priority()).isEqualTo("HIGH");
    assertThat(data.leadUserIds()).containsExactly(1L, 3L);
    verify(leadRepo).flush();
    verify(memberRepo).flush();
  }

  @Test
  void create_invalidLeadUserId_throwsNotFound() {
    CategoryMaster category = category(10L, "INF", "Infrastructure");
    when(projectRepo.findByNameIgnoreCase("BadLead")).thenReturn(Optional.empty());
    when(categoryRepo.findById(10L)).thenReturn(Optional.of(category));
    when(projectRepo.saveAndFlush(any(ProjectMaster.class)))
        .thenAnswer(
            inv -> {
              ProjectMaster p = inv.getArgument(0);
              p.setId(101L);
              return p;
            });
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepo.findByIdIn(any())).thenReturn(List.of()); // no user found

    ProjectDto.CreateRequest request =
        new ProjectDto.CreateRequest("BadLead", 10L, null, null, List.of(999999L), null);

    assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void update_keepsNumberAndCategoryLocked_andNotifiesOnLifecycleAndPriorityChange() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.findByNameIgnoreCaseAndIdNot("Apollo II", 1L)).thenReturn(Optional.empty());
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(leadRepo.findByProject_Id(1L)).thenReturn(List.of(leadMapping(1L)));
    when(memberRepo.findByProject_Id(1L)).thenReturn(List.of());

    ProjectDto.UpdateRequest request =
        new ProjectDto.UpdateRequest("Apollo II", "CRITICAL", "ACTIVE", "upd", null, null);

    ApiResponse<?> response = service.update(1L, request);

    ProjectDto.Detail data = (ProjectDto.Detail) response.getData();
    assertThat(data.projectNumber()).isEqualTo("INF-00001");
    assertThat(data.categoryId()).isEqualTo(10L);
    assertThat(data.priority()).isEqualTo("CRITICAL");
    assertThat(data.lifecycleStatus()).isEqualTo("ACTIVE");
    verify(notificationService)
        .notifyLifecycleChanged(eq(existing), eq("DRAFT"), eq("ACTIVE"), anyList());
    verify(notificationService)
        .notifyPriorityChanged(eq(existing), eq("MEDIUM"), eq("CRITICAL"), anyList());
  }

  @Test
  void updatePriority_changesValueAndNotifies() {
    ProjectMaster existing = existingProject();
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));
    when(leadRepo.findByProject_Id(1L)).thenReturn(List.of(leadMapping(1L)));

    ApiResponse<?> response = service.updatePriority(1L, "high");

    assertThat(response.getMessage()).contains("priority");
    assertThat(existing.getPriority()).isEqualTo("HIGH");
    verify(notificationService)
        .notifyPriorityChanged(eq(existing), eq("MEDIUM"), eq("HIGH"), anyList());
  }

  @Test
  void updateLifecycle_sameStatus_doesNotNotify() {
    ProjectMaster existing = existingProject();
    existing.setLifecycleStatus("ACTIVE");
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existing));
    when(projectRepo.save(any(ProjectMaster.class))).thenAnswer(inv -> inv.getArgument(0));

    service.updateLifecycle(1L, "active");

    verifyNoInteractions(notificationService);
  }

  @Test
  void updatePriority_invalidValue_throwsApplicationException() {
    when(projectRepo.findById(1L)).thenReturn(Optional.of(existingProject()));

    assertThatThrownBy(() -> service.updatePriority(1L, "URGENT"))
        .isInstanceOf(ApplicationException.class);
    verify(notificationService, never()).notifyPriorityChanged(any(), any(), any(), anyList());
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
    p.setProjectNumber("INF-00001");
    p.setName("Apollo");
    p.setCategory(category(10L, "INF", "Infrastructure"));
    p.setPriority("MEDIUM");
    p.setLifecycleStatus("DRAFT");
    p.setActive(true);
    return p;
  }

  private static CategoryMaster category(long id, String prefix, String name) {
    CategoryMaster c = new CategoryMaster();
    c.setId(id);
    c.setPrefix(prefix);
    c.setName(name);
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

  private ProjectLeadMapping leadMapping(long userId) {
    ProjectLeadMapping m = new ProjectLeadMapping();
    m.setUserId(userId);
    return m;
  }

  private static UserAccountRef userRef(long id) {
    UserAccountRef ref = mock(UserAccountRef.class);
    when(ref.getId()).thenReturn(id);
    return ref;
  }
}
