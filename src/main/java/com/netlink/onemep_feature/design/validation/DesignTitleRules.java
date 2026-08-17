package com.netlink.onemep_feature.design.validation;

import com.netlink.onemep_feature.exception.ApplicationException;
import java.util.regex.Pattern;

/**
 * Design Title rules, shared by Add and Edit (ONEMEP-36/37).
 *
 * <p>ONEMEP-36 defers the maximum length and approved character set to "the agreed Design Title
 * standard", which was never supplied. The length cap here matches the column; the only content
 * rule the tickets state explicitly — at least one letter — is enforced.
 */
public final class DesignTitleRules {
  private DesignTitleRules() {}

  public static final int MAX_LENGTH = 200;

  private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*", Pattern.DOTALL);

  /** Trims and validates a Title, returning the value to persist. */
  public static String requireValid(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      throw new ApplicationException("Title is required.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new ApplicationException("Title cannot exceed " + MAX_LENGTH + " characters.");
    }
    if (!HAS_LETTER.matcher(value).matches()) {
      throw new ApplicationException(
          "Title must contain at least one letter and cannot consist only of numbers, spaces, or"
              + " special characters.");
    }
    return value;
  }

  /**
   * The form the duplicate check compares. ONEMEP-36/37 require surrounding spaces not to defeat
   * duplicate detection; lower-casing additionally makes it case-insensitive, consistent with every
   * other uniqueness rule in this service.
   */
  public static String normalize(String validatedTitle) {
    return validatedTitle.trim().toLowerCase();
  }
}
