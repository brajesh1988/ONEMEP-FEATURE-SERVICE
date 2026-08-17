package com.netlink.onemep_feature.designimport.service;

import java.util.List;

/**
 * The outcome of validating one row: either something importable, or the reasons it is not.
 *
 * <p>A sealed pair rather than a nullable value plus a list, so a caller cannot read the row
 * without having established that it is valid — partial success means both branches are ordinary,
 * expected outcomes and neither may be handled by accident.
 */
public sealed interface RowValidationResult {

  /** The row is importable. */
  record Valid(ValidatedRow row) implements RowValidationResult {}

  /**
   * The row is not importable, for at least one reason.
   *
   * <p>Plural by design: the Design Number rule and the Title rule are independent, so a row
   * breaking both is reported against both. Collapsing them would hide half of what the user has to
   * fix and guarantee a second failed attempt.
   */
  record Rejected(List<RowProblem> problems) implements RowValidationResult {
    public Rejected {
      problems = List.copyOf(problems);
    }
  }

  /** The row was empty — trailing formatting, not a Design. Neither imported nor reported. */
  record Skipped() implements RowValidationResult {}
}
