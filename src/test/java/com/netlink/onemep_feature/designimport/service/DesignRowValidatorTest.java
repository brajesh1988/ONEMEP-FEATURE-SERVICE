package com.netlink.onemep_feature.designimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.netlink.onemep_feature.design.model.WorkProgress;
import com.netlink.onemep_feature.design.service.DesignUniquenessGuard;
import com.netlink.onemep_feature.designimport.parser.ImportColumn;
import com.netlink.onemep_feature.designimport.parser.SheetRow;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.service.LookupService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Row-level validation, including the two duplicate rules ONEMEP-35 specifies.
 *
 * <p>The uniqueness guard is mocked so the rules can be exercised independently of each other —
 * which is the whole point of the business ruling: they are two rules, not a composite key, and a
 * row can fail either one alone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesignRowValidatorTest {

  private static final Long PROJECT_ID = 7L;
  private static final String PROJECT_CODE = "40012";

  @Mock private LookupService lookupService;
  @Mock private DesignUniquenessGuard uniquenessGuard;

  private DesignRowValidator validator;
  private BatchDuplicateIndex seen;

  @BeforeEach
  void setUp() {
    validator = new DesignRowValidator(lookupService, uniquenessGuard);
    seen = new BatchDuplicateIndex();
    seen.enterFile("designs.xlsx");

    given(lookupService.requireActiveByCode(eq(LookupType.DISCIPLINE), any()))
        .willAnswer(invocation -> lookup(1L, invocation.getArgument(1)));
    given(lookupService.requireActiveByCode(eq(LookupType.DESIGN_TYPE), any()))
        .willAnswer(invocation -> lookup(2L, invocation.getArgument(1)));
    given(lookupService.requireActiveByCode(eq(LookupType.SUBJECT), any()))
        .willAnswer(invocation -> lookup(3L, invocation.getArgument(1)));
    given(lookupService.requireActiveByCode(eq(LookupType.FLOOR), any()))
        .willAnswer(invocation -> lookup(4L, invocation.getArgument(1)));
    given(lookupService.requireActiveByCode(eq(LookupType.STAGE), any()))
        .willAnswer(invocation -> lookup(5L, invocation.getArgument(1)));
  }

  // ── the happy path ────────────────────────────────────────────────────────

  @Test
  void validate_withACompleteRow_generatesTheDesignNumberFromItsSegments() {
    RowValidationResult result = validator.validate(PROJECT_ID, PROJECT_CODE, row(14), seen);

    assertThat(result).isInstanceOf(RowValidationResult.Valid.class);
    ValidatedRow valid = ((RowValidationResult.Valid) result).row();
    assertThat(valid.designNumber()).isEqualTo("ONEMEP-40012-Z01-M-SCH-CHW-00-DD");
    assertThat(valid.title()).isEqualTo("Chilled water schematic");
    assertThat(valid.titleNormalized()).isEqualTo("chilled water schematic");
    assertThat(valid.workProgress()).isEqualTo(WorkProgress.NOT_STARTED);
  }

  @Test
  void validate_withNoZone_fallsBackToTheXxPlaceholder() {
    Map<ImportColumn, String> cells = cells();
    cells.remove(ImportColumn.ZONE);

    ValidatedRow valid =
        requireValid(validator.validate(PROJECT_ID, PROJECT_CODE, row(2, cells), seen));
    assertThat(valid.designNumber()).isEqualTo("ONEMEP-40012-XX-M-SCH-CHW-00-DD");
  }

  @Test
  void validate_withABlankRow_isSkippedRatherThanReported() {
    SheetRow blank = new SheetRow(99, Map.of());

    assertThat(validator.validate(PROJECT_ID, PROJECT_CODE, blank, seen))
        .isInstanceOf(RowValidationResult.Skipped.class);
  }

  // ── duplicate rule one: Design Number ─────────────────────────────────────

  @Test
  void validate_whenTheDesignNumberExistsInTheRegister_reportsTheTicketsWording() {
    willThrow(
            new DuplicateResourceException(
                "Design Number 'ONEMEP-40012-Z01-M-SCH-CHW-00-DD' already exists in this Project."))
        .given(uniquenessGuard)
        .requireUniqueDesignNumber(anyLong(), any(), any());

    List<RowProblem> problems =
        requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(14), seen));

    assertThat(problems)
        .extracting(RowProblem::message)
        .containsExactly(
            "Row 14 — Design Number 'ONEMEP-40012-Z01-M-SCH-CHW-00-DD' already exists in this"
                + " Project.");
  }

  // ── duplicate rule two: Title ─────────────────────────────────────────────

  @Test
  void validate_whenOnlyTheTitleExists_isStillRejected() {
    willThrow(
            new DuplicateResourceException(
                "A Design with this Title already exists in this Project."))
        .given(uniquenessGuard)
        .requireUniqueTitle(anyLong(), any(), any());

    List<RowProblem> problems =
        requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(14), seen));

    assertThat(problems)
        .extracting(RowProblem::message)
        .containsExactly("Row 14 — A Design with this Title already exists in this Project.");
  }

  /** The rules are independent, so a row breaking both must be told about both. */
  @Test
  void validate_whenBothTheNumberAndTheTitleExist_reportsTwoSeparateProblems() {
    willThrow(new DuplicateResourceException("Design Number 'X' already exists in this Project."))
        .given(uniquenessGuard)
        .requireUniqueDesignNumber(anyLong(), any(), any());
    willThrow(
            new DuplicateResourceException(
                "A Design with this Title already exists in this Project."))
        .given(uniquenessGuard)
        .requireUniqueTitle(anyLong(), any(), any());

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(14), seen)))
        .hasSize(2);
  }

  /** A different number AND a different title is the only combination that passes. */
  @Test
  void validate_whenNeitherTheNumberNorTheTitleExists_isValid() {
    assertThat(validator.validate(PROJECT_ID, PROJECT_CODE, row(14), seen))
        .isInstanceOf(RowValidationResult.Valid.class);
  }

  // ── duplicates against earlier rows of the same batch ─────────────────────

  @Test
  void validate_whenAnEarlierRowInTheBatchClaimedTheNumber_namesBothRows() {
    seen.claim(12, "ONEMEP-40012-Z01-M-SCH-CHW-00-DD", "something else entirely");

    List<RowProblem> problems =
        requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(18), seen));

    assertThat(problems)
        .extracting(RowProblem::message)
        .containsExactly("Rows 12 and 18 contain the same Design Number.");
  }

  @Test
  void validate_whenAnEarlierRowInTheBatchClaimedTheTitle_namesBothRows() {
    seen.claim(12, "ONEMEP-40012-ZZZ-M-SCH-CHW-00-DD", "chilled water schematic");

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(18), seen)))
        .extracting(RowProblem::message)
        .containsExactly("Rows 12 and 18 contain the same Title.");
  }

  /** Two files in one batch are one submission, so the message has to say which file. */
  @Test
  void validate_whenTheEarlierRowWasInAnotherFileOfTheBatch_namesThatFile() {
    seen.enterFile("zone-a.xlsx");
    seen.claim(12, "ONEMEP-40012-Z01-M-SCH-CHW-00-DD", "an unrelated title");
    seen.enterFile("zone-b.xlsx");

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(18), seen)))
        .extracting(RowProblem::message)
        .containsExactly("Rows 12 of 'zone-a.xlsx' and 18 contain the same Design Number.");
  }

  // ── field rules ───────────────────────────────────────────────────────────

  @Test
  void validate_withAMissingSegment_saysThatColumnIsRequired() {
    Map<ImportColumn, String> cells = cells();
    cells.remove(ImportColumn.DISCIPLINE);

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen)))
        .extracting(RowProblem::message)
        .containsExactly("Row 5 — Discipline is required.");
  }

  @Test
  void validate_withAnUnknownSegmentCode_reportsTheCatalogueMessage() {
    given(lookupService.requireActiveByCode(eq(LookupType.SUBJECT), any()))
        .willThrow(new ResourceNotFoundException("Subject 'XYZ' is not configured."));

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5), seen)))
        .extracting(RowProblem::message)
        .containsExactly("Row 5 — Subject 'XYZ' is not configured.");
  }

  @Test
  void validate_withATitleOfOnlyDigits_appliesTheSameRuleAsAddDesign() {
    Map<ImportColumn, String> cells = cells();
    cells.put(ImportColumn.TITLE, "12345");

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen)))
        .extracting(RowProblem::message)
        .allSatisfy(message -> assertThat(message).startsWith("Row 5 — Title must contain"));
  }

  @Test
  void validate_collectsEveryFailureRatherThanStoppingAtTheFirst() {
    Map<ImportColumn, String> cells = cells();
    cells.remove(ImportColumn.DISCIPLINE);
    cells.remove(ImportColumn.STAGE);
    cells.remove(ImportColumn.TITLE);

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen)))
        .hasSize(3);
  }

  @Test
  void validate_withWorkProgressInDisplayWording_isAccepted() {
    Map<ImportColumn, String> cells = cells();
    cells.put(ImportColumn.WORK_PROGRESS, "In Progress");

    assertThat(
            requireValid(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen))
                .workProgress())
        .isEqualTo(WorkProgress.IN_PROGRESS);
  }

  @Test
  void validate_withAnUnknownWorkProgress_listsTheValidOptions() {
    Map<ImportColumn, String> cells = cells();
    cells.put(ImportColumn.WORK_PROGRESS, "Nearly done");

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen)))
        .extracting(RowProblem::message)
        .containsExactly(
            "Row 5 — Work Progress 'Nearly done' is not valid. Use one of: Not Started, In"
                + " Progress, Issued, Completed.");
  }

  @Test
  void validate_withAnInvalidZone_reportsItAgainstTheZoneColumn() {
    Map<ImportColumn, String> cells = cells();
    cells.put(ImportColumn.ZONE, "Z-01!");

    assertThat(requireRejected(validator.validate(PROJECT_ID, PROJECT_CODE, row(5, cells), seen)))
        .extracting(RowProblem::column)
        .containsExactly(ImportColumn.ZONE);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static LookupValue lookup(Long id, String code) {
    LookupValue value = new LookupValue();
    value.setId(id);
    value.setCode(code == null ? null : code.toUpperCase(java.util.Locale.ROOT));
    value.setLabel(code);
    value.setActive(true);
    return value;
  }

  private static Map<ImportColumn, String> cells() {
    Map<ImportColumn, String> cells = new EnumMap<>(ImportColumn.class);
    cells.put(ImportColumn.ZONE, "Z01");
    cells.put(ImportColumn.DISCIPLINE, "M");
    cells.put(ImportColumn.TYPE, "SCH");
    cells.put(ImportColumn.SUBJECT, "CHW");
    cells.put(ImportColumn.FLOOR, "00");
    cells.put(ImportColumn.STAGE, "DD");
    cells.put(ImportColumn.TITLE, "Chilled water schematic");
    return cells;
  }

  private static SheetRow row(int rowNumber) {
    return row(rowNumber, cells());
  }

  private static SheetRow row(int rowNumber, Map<ImportColumn, String> cells) {
    return new SheetRow(rowNumber, cells);
  }

  private static ValidatedRow requireValid(RowValidationResult result) {
    assertThat(result).isInstanceOf(RowValidationResult.Valid.class);
    return ((RowValidationResult.Valid) result).row();
  }

  private static List<RowProblem> requireRejected(RowValidationResult result) {
    assertThat(result).isInstanceOf(RowValidationResult.Rejected.class);
    return ((RowValidationResult.Rejected) result).problems();
  }
}
