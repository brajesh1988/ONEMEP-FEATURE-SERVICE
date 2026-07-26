package com.netlink.onemep_feature.common.sequence;

/**
 * Reusable helpers to build codes of the shape {@code prefix + number + suffix} from a {@link
 * NumberSequence}. Pure and side-effect-free apart from mutating the passed entity's counter in
 * {@link #allocate}; keep the concurrency guard (a pessimistic lock on the entity row) in the repo
 * layer so concurrent allocations of the same sequence serialize and never collide.
 */
public final class SequenceNumbers {
  private SequenceNumbers() {}

  /**
   * Increments the entity's running counter ({@code null} → 1) and returns the formatted code. The
   * entity MUST be a JPA-managed instance loaded under a pessimistic write lock; the incremented
   * {@code lastNumber} is flushed by dirty checking, so no explicit save is required.
   *
   * @param pad minimum digit width for the number (zero-padded); {@code pad <= 0} means no padding
   */
  public static String allocate(NumberSequence sequence, int pad) {
    int next = sequence.getLastNumber() == null ? 1 : sequence.getLastNumber() + 1;
    sequence.setLastNumber(next);
    return format(sequence.getPrefix(), next, pad, sequence.getSuffix());
  }

  /** e.g. {@code format("HTL", 12, 4, "A")} → {@code "HTL0012A"}. */
  public static String format(String prefix, int number, int pad, String suffix) {
    String body = pad > 0 ? String.format("%0" + pad + "d", number) : Integer.toString(number);
    return nullToEmpty(prefix) + body + nullToEmpty(suffix);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
