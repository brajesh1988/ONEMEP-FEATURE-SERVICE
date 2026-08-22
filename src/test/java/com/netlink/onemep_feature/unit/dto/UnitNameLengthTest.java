package com.netlink.onemep_feature.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Unit name is capped at 50 characters on create and update alike. */
class UnitNameLengthTest {

  private static final int MAX = 50;

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

  private static Set<String> createViolations(String name) {
    return paths(validator.validate(new UnitDto.CreateRequest(name, "mm", "NUMERIC", true)));
  }

  private static Set<String> updateViolations(String name) {
    return paths(validator.validate(new UnitDto.UpdateRequest(name, "mm", "NUMERIC", true)));
  }

  private static Set<String> paths(Set<? extends jakarta.validation.ConstraintViolation<?>> v) {
    return v.stream().map(x -> x.getPropertyPath().toString()).collect(Collectors.toSet());
  }

  @Test
  void createRequest_nameAtLimit_isAccepted() {
    assertThat(createViolations("a".repeat(MAX))).doesNotContain("name");
  }

  @Test
  void createRequest_nameOverLimit_isRejected() {
    assertThat(createViolations("a".repeat(MAX + 1))).contains("name");
  }

  @Test
  void updateRequest_nameOverLimit_isRejected() {
    // Update carried its own copy of the constraint, so it needs its own guard.
    assertThat(updateViolations("a".repeat(MAX + 1))).contains("name");
  }

  @Test
  void createRequest_blankName_isRejected() {
    assertThat(createViolations("  ")).contains("name");
  }
}
