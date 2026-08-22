package com.netlink.onemep_feature.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DID contact number rules: digits plus "+" and "-" only, 15 characters maximum, blank allowed. */
class DidContactValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static Set<String> violationsFor(String contactNo) {
    DidSpecificationDto.ContactRow row =
        new DidSpecificationDto.ContactRow(
            null, "Lead Engineer", "Priya", "priya@example.com", contactNo, false);
    return validator.validate(row).stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  @ParameterizedTest
  @ValueSource(strings = {"9876543210", "+919876543210", "020-1234-5678", "1", "+", "-"})
  void contactNo_withDigitsPlusOrHyphen_isAccepted(String value) {
    assertThat(violationsFor(value)).doesNotContain("contactNo");
  }

  @Test
  void contactNo_whenBlank_isAccepted() {
    // The frontend sends "" for a field the user left empty; that must not be an error.
    assertThat(violationsFor("")).doesNotContain("contactNo");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "98765abcde", // alphabets are not allowed
        "(020) 12345678", // parentheses and spaces are no longer allowed
        "1234567890123456", // 16 characters — one over the limit
        "+91 98765 43210" // spaces
      })
  void contactNo_withDisallowedCharactersOrTooLong_isRejected(String value) {
    assertThat(violationsFor(value)).contains("contactNo");
  }

  @Test
  void contactNo_atExactlyFifteenCharacters_isAccepted() {
    assertThat(violationsFor("123456789012345")).doesNotContain("contactNo");
  }
}
