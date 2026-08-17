package com.netlink.onemep_feature.design.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.design.dto.DesignTaskDto;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.model.TaskPriority;
import com.netlink.onemep_feature.design.model.TaskReminder;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.design.repo.DesignTagRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
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

/** Task-section business rules (ONEMEP-38). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesignTaskServiceImplTest {

  private static final long DESIGN_ID = 5L;
  private static final long PROJECT_ID = 3L;

  @Mock private DesignRepo designRepo;
  @Mock private DesignTagRepo designTagRepo;
  @Mock private ProjectMemberMappingRepo projectMemberMappingRepo;
  @Mock private UserDirectoryClient userDirectoryClient;
  @Mock private DesignActivityService designActivityService;

  private DesignTaskServiceImpl service;
  private Design design;

  @BeforeEach
  void setUp() {
    service =
        new DesignTaskServiceImpl(
            designRepo,
            designTagRepo,
            projectMemberMappingRepo,
            userDirectoryClient,
            designActivityService,
            new ApiResponseAdaptor());

    design = new Design();
    design.setId(DESIGN_ID);
    ProjectMaster project = new ProjectMaster();
    project.setId(PROJECT_ID);
    design.setProject(project);

    when(designRepo.findById(DESIGN_ID)).thenReturn(Optional.of(design));
    when(designTagRepo.findForDesign(DESIGN_ID)).thenReturn(List.of());
    when(userDirectoryClient.resolve(anyList()))
        .thenAnswer(
            inv -> {
              List<Long> ids = inv.getArgument(0);
              return ids.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          id -> (Long) id, id -> new UserSummary((Long) id, "User " + id, null)));
            });
  }

  // ── owner ─────────────────────────────────────────────────────────────────

  @Test
  void update_ownerNotAMemberOfTheProject_isRejected() {
    when(projectMemberMappingRepo.findByProject_Id(PROJECT_ID)).thenReturn(List.of(member(11L)));

    assertThatThrownBy(() -> service.update(DESIGN_ID, ownerUpdate(99L)))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("no longer available");
    assertThat(design.getOwnerId()).isNull();
  }

  @Test
  void update_ownerWhoIsAProjectMember_isAccepted_andAudited() {
    when(projectMemberMappingRepo.findByProject_Id(PROJECT_ID)).thenReturn(List.of(member(11L)));

    service.update(DESIGN_ID, ownerUpdate(11L));

    assertThat(design.getOwnerId()).isEqualTo(11L);
    verify(designActivityService)
        .record(design, ActivityAction.OWNER_CHANGED, "Owner changed from (unassigned) to User 11");
  }

  @Test
  void update_clearOwner_unassignsAndAudits() {
    design.setOwnerId(11L);

    service.update(
        DESIGN_ID,
        new DesignTaskDto.UpdateRequest(null, true, null, null, null, null, null, null, null));

    assertThat(design.getOwnerId()).isNull();
    verify(designActivityService)
        .record(design, ActivityAction.OWNER_CHANGED, "Owner cleared (was User 11)");
  }

  // ── completion ────────────────────────────────────────────────────────────

  @Test
  void update_completionAtTheBounds_isAccepted() {
    service.update(DESIGN_ID, completionUpdate(0));
    assertThat(design.getCompletionPct()).isZero();

    service.update(DESIGN_ID, completionUpdate(100));
    assertThat(design.getCompletionPct()).isEqualTo(100);
  }

  @Test
  void update_completionOutsideZeroToHundred_isRejected() {
    assertThatThrownBy(() -> service.update(DESIGN_ID, completionUpdate(101)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Completion must be between 0 and 100%.");
    assertThatThrownBy(() -> service.update(DESIGN_ID, completionUpdate(-1)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Completion must be between 0 and 100%.");
  }

  @Test
  void update_completionAudit_namesBothValues() {
    design.setCompletionPct(60);

    service.update(DESIGN_ID, completionUpdate(75));

    verify(designActivityService)
        .record(design, ActivityAction.COMPLETION_CHANGED, "Completion updated from 60% to 75%");
  }

  // ── schedule ──────────────────────────────────────────────────────────────

  @Test
  void update_dueDateEarlierThanStartDate_isRejected() {
    assertThatThrownBy(
            () ->
                service.update(
                    DESIGN_ID,
                    scheduleUpdate(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 6, 23))))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Due Date cannot be earlier than Start Date.");
  }

  /** Changing only one date must still be checked against the stored value of the other. */
  @Test
  void update_movingStartPastTheStoredDueDate_isRejected() {
    design.setStartDate(LocalDate.of(2026, 6, 1));
    design.setDueDate(LocalDate.of(2026, 6, 10));

    assertThatThrownBy(
            () -> service.update(DESIGN_ID, scheduleUpdate(LocalDate.of(2026, 6, 20), null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Due Date cannot be earlier than Start Date.");
    assertThat(design.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void update_equalStartAndDueDates_areAllowed() {
    LocalDate day = LocalDate.of(2026, 6, 23);
    service.update(DESIGN_ID, scheduleUpdate(day, day));
    assertThat(design.durationDays()).contains(0L);
  }

  @Test
  void update_clearingTheDueDate_removesTheDerivedDuration() {
    design.setStartDate(LocalDate.of(2026, 6, 1));
    design.setDueDate(LocalDate.of(2026, 6, 10));

    service.update(
        DESIGN_ID,
        new DesignTaskDto.UpdateRequest(null, null, null, null, null, null, null, true, null));

    assertThat(design.getDueDate()).isNull();
    assertThat(design.durationDays()).isEmpty();
  }

  // ── nothing to do ─────────────────────────────────────────────────────────

  @Test
  void update_withNothingChanged_recordsNoActivity() {
    design.setPriority(TaskPriority.MEDIUM);
    design.setCompletionPct(0);

    service.update(
        DESIGN_ID,
        new DesignTaskDto.UpdateRequest(
            null, null, TaskPriority.MEDIUM, 0, null, null, null, null, TaskReminder.NONE));

    verify(designActivityService, never()).record(any(), any(), any());
  }

  // ── tags ──────────────────────────────────────────────────────────────────

  @Test
  void addTag_trimsAndAudits() {
    when(designTagRepo.findByDesignAndNormalizedLabel(DESIGN_ID, "plant room"))
        .thenReturn(Optional.empty());

    service.addTag(DESIGN_ID, new DesignTaskDto.AddTagRequest("  Plant Room  "));

    verify(designActivityService)
        .record(design, ActivityAction.TAG_ADDED, "Tag 'Plant Room' added");
  }

  @Test
  void addTag_whitespaceOnly_isRejected() {
    assertThatThrownBy(() -> service.addTag(DESIGN_ID, new DesignTaskDto.AddTagRequest("   ")))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Enter a valid Tag.");
    verify(designTagRepo, never()).save(any());
  }

  @Test
  void addTag_differingOnlyByCase_isADuplicate() {
    when(designTagRepo.findByDesignAndNormalizedLabel(DESIGN_ID, "plant room"))
        .thenReturn(Optional.of(new com.netlink.onemep_feature.design.model.DesignTag()));

    assertThatThrownBy(
            () -> service.addTag(DESIGN_ID, new DesignTaskDto.AddTagRequest("plant room")))
        .isInstanceOf(com.netlink.onemep_feature.exception.DuplicateResourceException.class)
        .hasMessage("This Tag is already added to the Design.");
  }

  // ── view ──────────────────────────────────────────────────────────────────

  @Test
  void toView_exposesDerivedDurationAndResolvedReminderDate() {
    design.setStartDate(LocalDate.of(2026, 6, 23));
    design.setDueDate(LocalDate.of(2026, 7, 10));
    design.setReminder(TaskReminder.THREE_DAYS_BEFORE);

    DesignTaskDto.View view = service.toView(design);

    assertThat(view.durationDays()).isEqualTo(17L);
    assertThat(view.reminderDate()).isEqualTo(LocalDate.of(2026, 7, 7));
  }

  @Test
  void toView_withoutAnOwner_reportsNoOwnerRatherThanAPlaceholder() {
    assertThat(service.toView(design).owner()).isNull();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ProjectMemberMapping member(Long userId) {
    ProjectMemberMapping m = new ProjectMemberMapping();
    m.setUserId(userId);
    return m;
  }

  private static DesignTaskDto.UpdateRequest ownerUpdate(Long ownerId) {
    return new DesignTaskDto.UpdateRequest(ownerId, null, null, null, null, null, null, null, null);
  }

  private static DesignTaskDto.UpdateRequest completionUpdate(Integer pct) {
    return new DesignTaskDto.UpdateRequest(null, null, null, pct, null, null, null, null, null);
  }

  private static DesignTaskDto.UpdateRequest scheduleUpdate(LocalDate start, LocalDate due) {
    return new DesignTaskDto.UpdateRequest(null, null, null, null, start, null, due, null, null);
  }
}
