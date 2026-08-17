package com.netlink.onemep_feature.designimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The summary wording is an acceptance criterion of ONEMEP-35, so it is asserted literally. */
class ImportSummaryTest {

  @Test
  void of_withAPartialImport_matchesTheTicketsExample() {
    assertThat(ImportSummary.of(42, 50))
        .isEqualTo("42 of 50 Designs imported. 8 rows require correction.");
  }

  @Test
  void of_whenEveryRowImported_reportsOnlyTheCount() {
    assertThat(ImportSummary.of(50, 50)).isEqualTo("50 Designs imported.");
  }

  @Test
  void of_withASingleRow_staysGrammatical() {
    assertThat(ImportSummary.of(1, 1)).isEqualTo("1 Design imported.");
    assertThat(ImportSummary.of(0, 1))
        .isEqualTo("No Designs were imported. 1 row requires correction.");
    assertThat(ImportSummary.of(9, 10))
        .isEqualTo("9 of 10 Designs imported. 1 row requires correction.");
  }

  @Test
  void of_whenNothingImported_saysSoRatherThanReportingZero() {
    assertThat(ImportSummary.of(0, 50))
        .isEqualTo("No Designs were imported. 50 rows require correction.");
  }

  @Test
  void of_withNoRowsAtAll_distinguishesAnEmptySheetFromAFailedOne() {
    assertThat(ImportSummary.of(0, 0)).isEqualTo("No Design rows were found.");
  }
}
