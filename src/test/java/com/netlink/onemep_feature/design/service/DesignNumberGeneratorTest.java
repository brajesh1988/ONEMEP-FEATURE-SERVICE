package com.netlink.onemep_feature.design.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netlink.onemep_feature.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Design Number construction (ONEMEP-36). */
class DesignNumberGeneratorTest {

  @Test
  void generate_producesTheTicketsWorkedExample() {
    assertThat(DesignNumberGenerator.generate("40012", "Z01", "M", "SCH", "CHW", "L01", "DD"))
        .isEqualTo("ONEMEP-40012-Z01-M-SCH-CHW-L01-DD");
  }

  @Test
  void generate_usesXxForAnOmittedZone() {
    assertThat(
            DesignNumberGenerator.generate(
                "40012", DesignNumberGenerator.normalizeZone(null), "M", "SCH", "CHW", "00", "DD"))
        .isEqualTo("ONEMEP-40012-XX-M-SCH-CHW-00-DD");
  }

  @Test
  void generate_neverLeavesAnEmptyPosition() {
    // Even if a code somehow arrives blank, the number stays well-formed rather than producing
    // "ONEMEP-40012--M--CHW--DD".
    assertThat(DesignNumberGenerator.generate("40012", "XX", "M", "", "CHW", null, "DD"))
        .isEqualTo("ONEMEP-40012-XX-M-XX-CHW-XX-DD");
  }

  @Test
  void generate_withoutAProjectCode_isBlocked() {
    assertThatThrownBy(
            () -> DesignNumberGenerator.generate("  ", "XX", "M", "SCH", "CHW", "00", "DD"))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("does not have a valid Project Code");
  }

  @Test
  void normalizeZone_trimsAndUpperCases() {
    assertThat(DesignNumberGenerator.normalizeZone("  z01 ")).isEqualTo("Z01");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void normalizeZone_blankBecomesXx(String raw) {
    assertThat(DesignNumberGenerator.normalizeZone(raw)).isEqualTo("XX");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Z-01", "Z 01", "ZONE_01", "ABCDEFGHIJK"})
  void normalizeZone_rejectsUnsupportedFormats(String raw) {
    assertThatThrownBy(() -> DesignNumberGenerator.normalizeZone(raw))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("letters and digits");
  }
}
