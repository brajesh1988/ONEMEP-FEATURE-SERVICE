package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.design.model.WorkProgress;
import com.netlink.onemep_feature.design.service.DesignNumberGenerator;
import com.netlink.onemep_feature.design.service.DesignUniquenessGuard;
import com.netlink.onemep_feature.design.validation.DesignTitleRules;
import com.netlink.onemep_feature.designimport.parser.ImportColumn;
import com.netlink.onemep_feature.designimport.parser.SheetRow;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.service.LookupService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates one spreadsheet row against every rule the Design Register applies to a manually
 * created Design (ONEMEP-35).
 *
 * <p>The rules are deliberately the same objects Add Design uses — {@link DesignTitleRules}, {@link
 * DesignNumberGenerator} and {@link DesignUniquenessGuard} — not re-implementations of them. An
 * importer with its own copy of the duplicate rule is an importer that will drift from the screen
 * and start admitting records the screen rejects.
 *
 * <p><b>Every rule is evaluated, not just the first that fails.</b> Returning on the first problem
 * would make correcting a spreadsheet an iterative guessing game; the ticket's whole premise is
 * that the user fixes the reported rows and re-uploads once.
 *
 * <p>The two duplicate rules in particular are run as separate validations producing separate
 * messages, per the business ruling: same number with a different title is rejected, a different
 * number with the same title is rejected, and only a row differing in both is valid.
 */
@Component
@RequiredArgsConstructor
public class DesignRowValidator {

  private final LookupService lookupService;
  private final DesignUniquenessGuard uniquenessGuard;

  /**
   * @param projectId the Project the batch was submitted against
   * @param projectCode the Project Number the Design Number embeds
   * @param seen numbers and titles claimed by earlier rows of this batch
   */
  public RowValidationResult validate(
      Long projectId, String projectCode, SheetRow row, BatchDuplicateIndex seen) {

    if (row.isBlank()) {
      return new RowValidationResult.Skipped();
    }

    List<RowProblem> problems = new ArrayList<>();
    int line = row.rowNumber();

    String zone = resolveZone(row, problems);
    LookupValue discipline = segment(row, ImportColumn.DISCIPLINE, LookupType.DISCIPLINE, problems);
    LookupValue type = segment(row, ImportColumn.TYPE, LookupType.DESIGN_TYPE, problems);
    LookupValue subject = segment(row, ImportColumn.SUBJECT, LookupType.SUBJECT, problems);
    LookupValue floor = segment(row, ImportColumn.FLOOR, LookupType.FLOOR, problems);
    LookupValue stage = segment(row, ImportColumn.STAGE, LookupType.STAGE, problems);

    String title = resolveTitle(row, problems);
    WorkProgress workProgress = resolveWorkProgress(row, problems);

    // The Design Number cannot be built until every segment resolved, so the duplicate rules can
    // only run on a row that got that far. A row failing here already has its own errors reported;
    // adding speculative duplicate messages on top would be noise.
    if (!problems.isEmpty()) {
      return new RowValidationResult.Rejected(problems);
    }

    String titleNormalized = DesignTitleRules.normalize(title);
    String designNumber =
        DesignNumberGenerator.generate(
            projectCode,
            zone,
            discipline.getCode(),
            type.getCode(),
            subject.getCode(),
            floor.getCode(),
            stage.getCode());

    checkDesignNumber(projectId, designNumber, line, seen, problems);
    checkTitle(projectId, titleNormalized, line, seen, problems);

    if (!problems.isEmpty()) {
      return new RowValidationResult.Rejected(problems);
    }

    return new RowValidationResult.Valid(
        new ValidatedRow(
            line,
            designNumber,
            zone,
            discipline,
            type,
            subject,
            floor,
            stage,
            title,
            titleNormalized,
            row.get(ImportColumn.SHEET_SIZE),
            row.get(ImportColumn.SCALE),
            row.get(ImportColumn.PREPARED_BY),
            workProgress));
  }

  // ── the two duplicate rules, independent of one another ───────────────────

  /**
   * Rule one: the Design Number must be free. Checked against rows earlier in this batch first,
   * because that message is the more actionable of the two, then against the register.
   */
  private void checkDesignNumber(
      Long projectId,
      String designNumber,
      int line,
      BatchDuplicateIndex seen,
      List<RowProblem> problems) {

    var earlier = seen.findDesignNumber(designNumber);
    if (earlier.isPresent()) {
      problems.add(
          RowProblem.verbatim(line, seen.collisionMessage(earlier.get(), line, "Design Number")));
      return;
    }
    try {
      uniquenessGuard.requireUniqueDesignNumber(projectId, designNumber, null);
    } catch (DuplicateResourceException e) {
      problems.add(RowProblem.at(line, e.getMessage()));
    }
  }

  /** Rule two: the Title must be free. Runs whatever rule one decided — they are not a pair. */
  private void checkTitle(
      Long projectId,
      String titleNormalized,
      int line,
      BatchDuplicateIndex seen,
      List<RowProblem> problems) {

    var earlier = seen.findTitle(titleNormalized);
    if (earlier.isPresent()) {
      problems.add(RowProblem.verbatim(line, seen.collisionMessage(earlier.get(), line, "Title")));
      return;
    }
    try {
      uniquenessGuard.requireUniqueTitle(projectId, titleNormalized, null);
    } catch (DuplicateResourceException e) {
      problems.add(RowProblem.at(line, ImportColumn.TITLE, e.getMessage()));
    }
  }

  // ── field rules ───────────────────────────────────────────────────────────

  /** Zone is the one optional segment; blank becomes {@code XX}, matching Add Design. */
  private String resolveZone(SheetRow row, List<RowProblem> problems) {
    try {
      return DesignNumberGenerator.normalizeZone(row.get(ImportColumn.ZONE));
    } catch (ApplicationException e) {
      problems.add(RowProblem.at(row.rowNumber(), ImportColumn.ZONE, e.getMessage()));
      return DesignNumberGenerator.EMPTY_SEGMENT;
    }
  }

  /**
   * Resolves a catalogue segment from its code. A blank cell and an unknown code are different
   * failures and read differently — "is required" versus "is not configured" — because the fix is
   * different.
   */
  private LookupValue segment(
      SheetRow row, ImportColumn column, LookupType type, List<RowProblem> problems) {

    String code = row.get(column);
    if (code == null) {
      problems.add(RowProblem.at(row.rowNumber(), column, column.header() + " is required."));
      return null;
    }
    try {
      return lookupService.requireActiveByCode(type, code);
    } catch (ResourceNotFoundException e) {
      problems.add(RowProblem.at(row.rowNumber(), column, e.getMessage()));
      return null;
    }
  }

  private String resolveTitle(SheetRow row, List<RowProblem> problems) {
    try {
      return DesignTitleRules.requireValid(row.get(ImportColumn.TITLE));
    } catch (ApplicationException e) {
      problems.add(RowProblem.at(row.rowNumber(), ImportColumn.TITLE, e.getMessage()));
      return null;
    }
  }

  /**
   * Work Progress is optional and defaults as Add Design does. Accepts the display wording a person
   * would actually type — "Not Started" as readily as "NOT_STARTED".
   */
  private WorkProgress resolveWorkProgress(SheetRow row, List<RowProblem> problems) {
    String raw = row.get(ImportColumn.WORK_PROGRESS);
    if (raw == null) {
      return WorkProgress.NOT_STARTED;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    for (WorkProgress candidate : WorkProgress.values()) {
      if (candidate.name().equals(normalized)) {
        return candidate;
      }
    }
    problems.add(
        RowProblem.at(
            row.rowNumber(),
            ImportColumn.WORK_PROGRESS,
            "Work Progress '"
                + raw
                + "' is not valid. Use one of: "
                + workProgressOptions()
                + "."));
    return WorkProgress.NOT_STARTED;
  }

  /** {@code NOT_STARTED} reads back as "Not Started" — the wording the spreadsheet would use. */
  private static String workProgressOptions() {
    return Arrays.stream(WorkProgress.values())
        .map(
            value ->
                Arrays.stream(value.name().split("_"))
                    .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" ")))
        .collect(Collectors.joining(", "));
  }
}
