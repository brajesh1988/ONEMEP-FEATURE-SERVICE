package com.netlink.onemep_feature.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netlink.onemep_feature.checklist.dto.ChecklistDto;
import com.netlink.onemep_feature.checklist.model.ApplicabilitySegment;
import com.netlink.onemep_feature.checklist.model.ChecklistApplicability;
import com.netlink.onemep_feature.checklist.model.ChecklistMaster;
import com.netlink.onemep_feature.checklist.model.ChecklistRecordType;
import com.netlink.onemep_feature.checklist.repo.ChecklistMasterRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.service.LookupService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Business rules for the Checklist Master (ONEMEP-32/33/34). */
@ExtendWith(MockitoExtension.class)
class ChecklistServiceImplTest {

  @Mock private ChecklistMasterRepo checklistMasterRepo;
  @Mock private LookupService lookupService;
  private ChecklistServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ChecklistServiceImpl(checklistMasterRepo, lookupService, new ApiResponseAdaptor());
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_checklist_numbersItemsFromOneAndContiguously() {
    stubSave();
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");
    when(checklistMasterRepo.findByNameIgnoreCase("Riser Checks")).thenReturn(Optional.empty());

    ApiResponse<?> response =
        service.create(
            new ChecklistDto.CreateRequest(
                ChecklistRecordType.CHECKLIST,
                "  Riser Checks  ",
                List.of("  Check title block  ", "Verify scale", "Check north point"),
                appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                null));

    ChecklistDto.Response data = (ChecklistDto.Response) response.getData();
    assertThat(data.name()).isEqualTo("Riser Checks");
    assertThat(data.items())
        .containsExactly("Check title block", "Verify scale", "Check north point");
    assertThat(data.active()).isTrue();
    assertThat(response.getMessage()).isEqualTo("Checklist created successfully.");
  }

  @Test
  void create_singleItem_storesNoNameAndExposesItemTextAsTheEntry() {
    stubSave();
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");

    ApiResponse<?> response =
        service.create(
            new ChecklistDto.CreateRequest(
                ChecklistRecordType.SINGLE_ITEM,
                null,
                List.of("Verify equipment clearance"),
                appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                null));

    ChecklistDto.Response data = (ChecklistDto.Response) response.getData();
    assertThat(data.name()).isNull();
    assertThat(data.items()).containsExactly("Verify equipment clearance");
    assertThat(response.getMessage()).isEqualTo("Single Item created successfully.");
  }

  @Test
  void create_singleItemWithAName_isRejectedRatherThanSilentlyDropped() {
    assertThatThrownBy(
            () ->
                service.create(
                    new ChecklistDto.CreateRequest(
                        ChecklistRecordType.SINGLE_ITEM,
                        "Should not be accepted",
                        List.of("Verify clearance"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("does not apply to a Single Item");
    verify(checklistMasterRepo, never()).save(any());
  }

  @Test
  void create_singleItemWithTwoItems_isRejected() {
    assertThatThrownBy(
            () ->
                service.create(
                    new ChecklistDto.CreateRequest(
                        ChecklistRecordType.SINGLE_ITEM,
                        null,
                        List.of("One", "Two"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("A Single Item must contain exactly one item.");
  }

  @Test
  void create_checklistWithThirtyOneItems_isRejected() {
    List<String> items =
        java.util.stream.IntStream.rangeClosed(1, 31).mapToObj(i -> "Item " + i).toList();

    assertThatThrownBy(
            () ->
                service.create(
                    new ChecklistDto.CreateRequest(
                        ChecklistRecordType.CHECKLIST,
                        "Too many",
                        items,
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("A Checklist can contain a maximum of 30 items.");
  }

  @Test
  void create_checklistWithThirtyItems_isAccepted() {
    stubSave();
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");
    when(checklistMasterRepo.findByNameIgnoreCase("Exactly thirty")).thenReturn(Optional.empty());

    List<String> items =
        java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(i -> "Item " + i).toList();

    ApiResponse<?> response =
        service.create(
            new ChecklistDto.CreateRequest(
                ChecklistRecordType.CHECKLIST,
                "Exactly thirty",
                items,
                appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                null));

    assertThat(((ChecklistDto.Response) response.getData()).items()).hasSize(30);
  }

  @Test
  void create_blankItemBetweenPopulatedOnes_failsAndIsNotSilentlyDropped() {
    assertThatThrownBy(
            () ->
                service.create(
                    new ChecklistDto.CreateRequest(
                        ChecklistRecordType.CHECKLIST,
                        "Has a gap",
                        List.of("Check drawing title", "   ", "Verify scale"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Item is required.");
    verify(checklistMasterRepo, never()).save(any());
  }

  @Test
  void create_duplicateNameIgnoringCase_isRejected() {
    // The service passes the trimmed value through as typed; case-insensitivity lives in the query.
    when(checklistMasterRepo.findByNameIgnoreCase("riser checks"))
        .thenReturn(Optional.of(new ChecklistMaster()));

    assertThatThrownBy(
            () ->
                service.create(
                    new ChecklistDto.CreateRequest(
                        ChecklistRecordType.CHECKLIST,
                        "riser checks",
                        List.of("One"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  // ── applicability ─────────────────────────────────────────────────────────

  @Test
  void create_emptySegmentList_becomesASingleWildcardRow() {
    stubSave();
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");
    when(checklistMasterRepo.findByNameIgnoreCase("Any discipline")).thenReturn(Optional.empty());

    ApiResponse<?> response =
        service.create(
            new ChecklistDto.CreateRequest(
                ChecklistRecordType.CHECKLIST,
                "Any discipline",
                List.of("Check something"),
                appliesTo(List.of(), List.of(2L), List.of(3L)),
                null));

    ChecklistDto.AppliesToView view = ((ChecklistDto.Response) response.getData()).appliesTo();
    assertThat(view.disciplines().any()).isTrue();
    assertThat(view.disciplines().values()).isEmpty();
    assertThat(view.types().any()).isFalse();
    assertThat(view.types().values())
        .extracting(ChecklistDto.ValueView::code)
        .containsExactly("SCH");
  }

  @Test
  void create_resolvesEachSegmentAgainstItsOwnCatalogue() {
    stubSave();
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");
    when(checklistMasterRepo.findByNameIgnoreCase("Typed")).thenReturn(Optional.empty());

    service.create(
        new ChecklistDto.CreateRequest(
            ChecklistRecordType.CHECKLIST,
            "Typed",
            List.of("Check something"),
            appliesTo(List.of(1L), List.of(2L), List.of(3L)),
            null));

    // The type guard lives in LookupService; assert each segment asks for its own catalogue so a
    // Subject id can never be accepted as a Discipline.
    verify(lookupService).requireAllActive(LookupType.DISCIPLINE, List.of(1L));
    verify(lookupService).requireAllActive(LookupType.DESIGN_TYPE, List.of(2L));
    verify(lookupService).requireAllActive(LookupType.SUBJECT, List.of(3L));
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void update_attemptingToChangeRecordType_isRejected() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.update(
                    5L,
                    new ChecklistDto.UpdateRequest(
                        ChecklistRecordType.SINGLE_ITEM,
                        "Riser Checks",
                        List.of("One"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Record type cannot be changed after creation.");
    verify(checklistMasterRepo, never()).save(any());
  }

  @Test
  void update_omittingRecordType_isAllowedAndKeepsTheStoredType() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));
    when(checklistMasterRepo.findByNameIgnoreCaseAndIdNot("Riser Checks v2", 5L))
        .thenReturn(Optional.empty());
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");

    service.update(
        5L,
        new ChecklistDto.UpdateRequest(
            null,
            "Riser Checks v2",
            List.of("Updated item"),
            appliesTo(List.of(1L), List.of(2L), List.of(3L)),
            false));

    assertThat(existing.getRecordType()).isEqualTo(ChecklistRecordType.CHECKLIST);
    assertThat(existing.getName()).isEqualTo("Riser Checks v2");
    assertThat(existing.getActive()).isFalse();
    assertThat(existing.getItems()).hasSize(1);
  }

  @Test
  void update_renamingToAnotherRecordsName_isRejected() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));
    when(checklistMasterRepo.findByNameIgnoreCaseAndIdNot("Taken", 5L))
        .thenReturn(Optional.of(new ChecklistMaster()));

    assertThatThrownBy(
            () ->
                service.update(
                    5L,
                    new ChecklistDto.UpdateRequest(
                        null,
                        "Taken",
                        List.of("One"),
                        appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                        null)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void update_keepingItsOwnName_isNotTreatedAsADuplicate() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));
    when(checklistMasterRepo.findByNameIgnoreCaseAndIdNot("Riser Checks", 5L))
        .thenReturn(Optional.empty());
    stubLookup(LookupType.DISCIPLINE, 1L, "M");
    stubLookup(LookupType.DESIGN_TYPE, 2L, "SCH");
    stubLookup(LookupType.SUBJECT, 3L, "CHW");

    ApiResponse<?> response =
        service.update(
            5L,
            new ChecklistDto.UpdateRequest(
                null,
                "Riser Checks",
                List.of("One"),
                appliesTo(List.of(1L), List.of(2L), List.of(3L)),
                null));

    assertThat(response.getMessage()).isEqualTo("Checklist updated successfully.");
  }

  @Test
  void update_missingRecord_reportsItIsNoLongerAvailable() {
    when(checklistMasterRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    99L,
                    new ChecklistDto.UpdateRequest(
                        null,
                        "X",
                        List.of("One"),
                        appliesTo(List.of(), List.of(), List.of()),
                        null)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("no longer available");
  }

  // ── status, impact, delete ────────────────────────────────────────────────

  @Test
  void updateStatus_deactivates_andReportsIt() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));

    ApiResponse<?> response = service.updateStatus(5L, false);

    assertThat(existing.getActive()).isFalse();
    assertThat(response.getMessage()).contains("deactivated");
  }

  @Test
  void impact_reportsTheEntryNameAndCurrentMatchCount() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));

    ChecklistDto.ImpactView data = (ChecklistDto.ImpactView) service.impact(5L).getData();

    assertThat(data.entryName()).isEqualTo("Riser Checks");
    // No design table exists yet, so nothing can match. Revisit with slice 3.
    assertThat(data.matchingDesignCount()).isZero();
  }

  @Test
  void delete_removesTheRecord() {
    ChecklistMaster existing = checklist(5L, ChecklistRecordType.CHECKLIST, "Riser Checks");
    when(checklistMasterRepo.findById(5L)).thenReturn(Optional.of(existing));

    ApiResponse<?> response = service.delete(5L);

    verify(checklistMasterRepo).delete(existing);
    assertThat(response.getMessage()).isEqualTo("Record deleted successfully.");
  }

  @Test
  void applicable_whenNothingMatches_saysSoInTheTicketsWords() {
    when(checklistMasterRepo.findApplicable(1L, 2L, 3L)).thenReturn(List.of());

    ApiResponse<?> response = service.applicable(1L, 2L, 3L);

    assertThat(response.getMessage())
        .isEqualTo("No checklist is configured for this Discipline, Type and Subject combination.");
    assertThat((List<?>) response.getData()).isEmpty();
  }

  @Test
  void applicable_singleItemRecord_reportsItsItemTextAsTheEntryName() {
    ChecklistMaster single = checklist(8L, ChecklistRecordType.SINGLE_ITEM, null);
    single.replaceItems(List.of("Verify equipment clearance"));
    when(checklistMasterRepo.findApplicable(1L, 2L, 3L)).thenReturn(List.of(single));

    @SuppressWarnings("unchecked")
    List<ChecklistDto.ApplicableItem> data =
        (List<ChecklistDto.ApplicableItem>) service.applicable(1L, 2L, 3L).getData();

    assertThat(data).hasSize(1);
    assertThat(data.get(0).entryName()).isEqualTo("Verify equipment clearance");
    assertThat(data.get(0).items()).containsExactly("Verify equipment clearance");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ChecklistDto.AppliesTo appliesTo(
      List<Long> disciplines, List<Long> types, List<Long> subjects) {
    return new ChecklistDto.AppliesTo(disciplines, types, subjects);
  }

  private void stubSave() {
    when(checklistMasterRepo.save(any(ChecklistMaster.class)))
        .thenAnswer(
            inv -> {
              ChecklistMaster c = inv.getArgument(0);
              c.setId(42L);
              return c;
            });
  }

  private void stubLookup(LookupType type, long id, String code) {
    when(lookupService.requireAllActive(eq(type), anyList())).thenReturn(List.of(value(id, code)));
  }

  private static LookupValue value(long id, String code) {
    LookupValue v = new LookupValue();
    v.setId(id);
    v.setCode(code);
    v.setLabel(code + " label");
    v.setActive(true);
    return v;
  }

  private static ChecklistMaster checklist(long id, ChecklistRecordType type, String name) {
    ChecklistMaster c = new ChecklistMaster();
    c.setId(id);
    c.setRecordType(type);
    c.setName(name);
    c.setActive(true);
    c.addApplicability(ChecklistApplicability.any(ApplicabilitySegment.DISCIPLINE));
    return c;
  }
}
