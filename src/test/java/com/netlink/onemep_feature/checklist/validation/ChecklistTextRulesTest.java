package com.netlink.onemep_feature.checklist.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netlink.onemep_feature.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ONEMEP-33/34 define two different character sets — an item may carry engineering notation a name
 * may not — so the two validators are tested independently rather than assumed identical.
 */
class ChecklistTextRulesTest {

  @Test
  void requireValidName_trimsButPreservesInternalSpaces() {
    assertThat(ChecklistTextRules.requireValidName("  Plan / Layout — Issue Checklist  "))
        .isEqualTo("Plan / Layout — Issue Checklist");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Plan / Layout — Issue Checklist",
        "Mechanical QA - Stage 1",
        "CHW Pump & Valve Checks",
        "Fire Protection (Level 01)",
        "HVAC_Review #2"
      })
  void requireValidName_acceptsTheTicketsWorkedExamples(String name) {
    assertThat(ChecklistTextRules.requireValidName(name)).isEqualTo(name);
  }

  @Test
  void requireValidName_blankOrWhitespaceOnly_isRequired() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("   "))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name is required.");
  }

  @Test
  void requireValidName_atExactlyFiftyCharacters_isAccepted() {
    String fifty = "A".repeat(50);
    assertThat(ChecklistTextRules.requireValidName(fifty)).hasSize(50);
  }

  @Test
  void requireValidName_overFiftyCharacters_isRejected() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("A".repeat(51)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name cannot exceed 50 characters.");
  }

  /** Every candidate uses only permitted characters, so the letter rule is what rejects them. */
  @ParameterizedTest
  @ValueSource(strings = {"12345", "---###", "123-456#", "(2024) [01]", "@#%"})
  void requireValidName_withoutAnyLetter_isRejected(String candidate) {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName(candidate))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("must contain at least one letter");
  }

  /**
   * The charset is checked before the letter rule, so a value that breaks both reports the
   * unsupported character — {@code $} is absent from the name set.
   */
  @Test
  void requireValidName_unsupportedCharacterIsReportedBeforeTheLetterRule() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("@#$%"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name contains unsupported characters.");
  }

  @Test
  void requireValidName_rejectsCharactersOnlyItemsMayUse() {
    // Backslash, curly braces, quotes and the engineering symbols belong to the item set only.
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("Plant {A}"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name contains unsupported characters.");
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("Duct 45°"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name contains unsupported characters.");
  }

  @Test
  void requireValidName_rejectsMarkup() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidName("<script>alert('x')</script>"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Name contains unsupported characters.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Verify equipment clearance",
        "Check clearance = 450mm",
        "Confirm angle 45° ± 2",
        "Duct size 300 × 200",
        "Path C:\\drawings\\riser",
        "Is the \"north point\" shown?",
        "Check {zone A} tagging!"
      })
  void requireValidItem_acceptsTheWiderEngineeringSet(String item) {
    assertThat(ChecklistTextRules.requireValidItem(item)).isEqualTo(item);
  }

  @Test
  void requireValidItem_atExactlyTwoHundredAndFiftyCharacters_isAccepted() {
    assertThat(ChecklistTextRules.requireValidItem("A".repeat(250))).hasSize(250);
  }

  @Test
  void requireValidItem_overTwoHundredAndFiftyCharacters_isRejected() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidItem("A".repeat(251)))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Item cannot exceed 250 characters.");
  }

  @Test
  void requireValidItem_whitespaceOnly_isTreatedAsEmpty() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidItem("   "))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Item is required.");
  }

  @Test
  void requireValidItem_withoutAnyLetter_isRejected() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidItem("450 ± 2"))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("must contain at least one letter");
  }

  @Test
  void requireValidItem_rejectsUnsupportedCharacters() {
    assertThatThrownBy(() -> ChecklistTextRules.requireValidItem("Check the ~riser~"))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Checklist Item contains unsupported characters.");
  }
}
