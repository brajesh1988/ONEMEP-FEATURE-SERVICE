package com.netlink.onemep_feature.design.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netlink.onemep_feature.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Design Title rules (ONEMEP-36/37). */
class DesignTitleRulesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Chilled water schematic",
        "Chilled water schematic - Level 01",
        "CHW Layout 02",
        "Plant Room - Zone A"
      })
  void requireValid_acceptsTheTicketsValidExamples(String title) {
    assertThat(DesignTitleRules.requireValid(title)).isEqualTo(title);
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345", "123-456", "---", "@#$%", "123@#$"})
  void requireValid_rejectsTheTicketsInvalidExamples(String title) {
    assertThatThrownBy(() -> DesignTitleRules.requireValid(title))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("must contain at least one letter");
  }

  @Test
  void requireValid_blankOrWhitespaceOnly_isRequired() {
    assertThatThrownBy(() -> DesignTitleRules.requireValid("   "))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Title is required.");
    assertThatThrownBy(() -> DesignTitleRules.requireValid(null))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Title is required.");
  }

  @Test
  void requireValid_trimsButKeepsInternalSpacing() {
    assertThat(DesignTitleRules.requireValid("  Chilled water   schematic  "))
        .isEqualTo("Chilled water   schematic");
  }

  @Test
  void normalize_makesSurroundingSpaceAndCaseIrrelevantToDuplicateDetection() {
    String a = DesignTitleRules.normalize(DesignTitleRules.requireValid("  Plant room layout  "));
    String b = DesignTitleRules.normalize(DesignTitleRules.requireValid("Plant Room Layout"));
    assertThat(a).isEqualTo(b);
  }
}
