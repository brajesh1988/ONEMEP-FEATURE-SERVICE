package com.netlink.onemep_feature.designimport.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImportColumnTest {

  @ParameterizedTest
  @ValueSource(strings = {"Sheet Size", "sheet size", "  SHEET   SIZE  ", "\tSheet\tSize\n"})
  void match_withWhitespaceAndCaseVariants_resolvesTheSameColumn(String header) {
    assertThat(ImportColumn.match(header)).contains(ImportColumn.SHEET_SIZE);
  }

  /** Excel exports routinely carry these and they are invisible in the spreadsheet. */
  @Test
  void match_withNonBreakingSpace_resolvesTheColumn() {
    assertThat(ImportColumn.match("Sheet Size")).contains(ImportColumn.SHEET_SIZE);
  }

  @Test
  void match_withLeadingByteOrderMark_resolvesTheColumn() {
    assertThat(ImportColumn.match("﻿Zone")).contains(ImportColumn.ZONE);
  }

  @Test
  void match_withTheTicketsAlternativeWording_resolvesTitle() {
    assertThat(ImportColumn.match("Design Title")).contains(ImportColumn.TITLE);
  }

  @Test
  void match_withAnUnknownHeader_resolvesNothing() {
    assertThat(ImportColumn.match("Cost Centre")).isEmpty();
    assertThat(ImportColumn.match("   ")).isEmpty();
    assertThat(ImportColumn.match(null)).isEmpty();
  }

  @Test
  void mapHeaders_recordsThePhysicalIndexOfEachColumn() {
    Map<ImportColumn, Integer> mapped =
        ImportColumn.mapHeaders(List.of("Notes", "Discipline", "", "Title"));

    assertThat(mapped)
        .containsEntry(ImportColumn.DISCIPLINE, 1)
        .containsEntry(ImportColumn.TITLE, 3)
        .doesNotContainKey(ImportColumn.ZONE);
  }

  /** A duplicated header must not shadow the column the user actually filled in first. */
  @Test
  void mapHeaders_withARepeatedHeader_keepsTheFirstOccurrence() {
    Map<ImportColumn, Integer> mapped = ImportColumn.mapHeaders(List.of("Title", "Zone", "Title"));

    assertThat(mapped).containsEntry(ImportColumn.TITLE, 0);
  }

  @Test
  void requiredColumns_areTheFiveSegmentsPlusTitle() {
    assertThat(ImportColumn.requiredColumns())
        .containsExactly(
            ImportColumn.DISCIPLINE,
            ImportColumn.TYPE,
            ImportColumn.SUBJECT,
            ImportColumn.FLOOR,
            ImportColumn.STAGE,
            ImportColumn.TITLE);
  }

  /** Zone is the one segment Add Design treats as optional; the importer must agree. */
  @Test
  void zone_isNotRequired() {
    assertThat(ImportColumn.ZONE.required()).isFalse();
  }
}
