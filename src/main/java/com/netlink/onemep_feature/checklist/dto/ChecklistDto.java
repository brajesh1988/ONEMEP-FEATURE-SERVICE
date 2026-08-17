package com.netlink.onemep_feature.checklist.dto;

import com.netlink.onemep_feature.checklist.model.ChecklistRecordType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for the Checklist Master (ONEMEP-32/33/34). */
public final class ChecklistDto {
  private ChecklistDto() {}

  /**
   * Applicability selections for one record.
   *
   * <p>An <b>empty list means "Any"</b> for that segment. Each list is {@code @NotNull} so an
   * omitted field is rejected rather than silently widening the record to match every Design — the
   * wildcard has to be chosen, not defaulted into.
   */
  public record AppliesTo(
      @NotNull(message = "Select at least one Discipline.") List<Long> disciplineIds,
      @NotNull(message = "Select at least one Type.") List<Long> typeIds,
      @NotNull(message = "Select at least one Subject.") List<Long> subjectIds) {

    public AppliesTo {
      disciplineIds = disciplineIds == null ? null : List.copyOf(disciplineIds);
      typeIds = typeIds == null ? null : List.copyOf(typeIds);
      subjectIds = subjectIds == null ? null : List.copyOf(subjectIds);
    }
  }

  /**
   * {@code recordType} decides which other fields apply: a CHECKLIST needs a name and 1-30 items, a
   * SINGLE_ITEM must have no name and exactly one item. Both are validated in the service, where
   * the ticket's exact messages live.
   */
  public record CreateRequest(
      @NotNull(message = "Record type is required.") ChecklistRecordType recordType,
      String name,
      @NotEmpty(message = "Checklist Item is required.") List<String> items,
      @Valid @NotNull(message = "Applicability is required.") AppliesTo appliesTo,
      Boolean active) {}

  /**
   * {@code recordType} is optional here and exists only so a manipulated request can be rejected
   * loudly. ONEMEP-34 requires the backend to refuse a type change independently of the UI; leaving
   * the field out entirely would make a tampered payload silently ignored rather than rejected.
   * Send it matching the stored type, or omit it.
   */
  public record UpdateRequest(
      ChecklistRecordType recordType,
      String name,
      @NotEmpty(message = "Checklist Item is required.") List<String> items,
      @Valid @NotNull(message = "Applicability is required.") AppliesTo appliesTo,
      Boolean active) {}

  /** A resolved catalogue value as shown in the Applies To column. */
  public record ValueView(Long id, String code, String label) {}

  /**
   * Applies To as displayed. {@code any = true} with an empty {@code values} list is how a wildcard
   * segment renders — the grid shows "Any" for it.
   */
  public record SegmentView(boolean any, List<ValueView> values) {}

  public record AppliesToView(SegmentView disciplines, SegmentView types, SegmentView subjects) {}

  /** One row of the Checklist master grid (ONEMEP-32). */
  public record ListItem(
      Long id,
      ChecklistRecordType recordType,
      String entryName,
      AppliesToView appliesTo,
      int itemCount,
      Boolean active,
      LocalDateTime updatedDate) {}

  /** Full record, for the Edit screen. */
  public record Response(
      Long id,
      ChecklistRecordType recordType,
      String name,
      List<String> items,
      AppliesToView appliesTo,
      Boolean active,
      Integer version,
      Long updatedBy,
      LocalDateTime updatedDate) {}

  /** Pre-delete impact, shown in the confirmation dialog. */
  public record ImpactView(Long id, String entryName, long matchingDesignCount) {}

  /** Applicable checklist for a Design, used when raising an approval (ONEMEP-40). */
  public record ApplicableItem(
      Long id, ChecklistRecordType recordType, String entryName, List<String> items) {}
}
