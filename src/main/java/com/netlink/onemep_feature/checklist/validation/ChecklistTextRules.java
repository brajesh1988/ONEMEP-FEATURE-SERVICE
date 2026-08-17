package com.netlink.onemep_feature.checklist.validation;

import com.netlink.onemep_feature.exception.ApplicationException;
import java.util.regex.Pattern;

/**
 * Character and content rules for Checklist Names and Checklist Items.
 *
 * <p>ONEMEP-33 and ONEMEP-34 specify two <em>different</em> allowlists — an item may contain
 * engineering notation a name may not ({@code \ { } " ? ! = ° ± ×}) — so these are deliberately two
 * patterns rather than one shared regex. Messages are reproduced verbatim from the tickets.
 */
public final class ChecklistTextRules {
  private ChecklistTextRules() {}

  public static final int NAME_MAX_LENGTH = 50;
  public static final int ITEM_MAX_LENGTH = 250;
  public static final int MAX_ITEMS = 30;

  /** Letters, digits, space and: - – — _ / &amp; . , : ; ( ) [ ] ' + # @ % */
  private static final Pattern NAME_ALLOWED =
      Pattern.compile("^[A-Za-z0-9 \\-\u2013\u2014_/&.,:;()\\[\\]'+#@%]+$");

  /** The name set plus: \ { } " ? ! = ° ± × */
  private static final Pattern ITEM_ALLOWED =
      Pattern.compile(
          "^[A-Za-z0-9 \\-\u2013\u2014_/\\\\&.,:;()\\[\\]{}'\"+#@%?!=\u00B0\u00B1\u00D7]+$");

  private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*", Pattern.DOTALL);

  /** Trims and validates a Checklist Name, returning the value to persist. */
  public static String requireValidName(String raw) {
    String value = trim(raw);
    if (value.isEmpty()) {
      throw new ApplicationException("Checklist Name is required.");
    }
    if (value.length() > NAME_MAX_LENGTH) {
      throw new ApplicationException("Checklist Name cannot exceed 50 characters.");
    }
    if (!NAME_ALLOWED.matcher(value).matches()) {
      throw new ApplicationException("Checklist Name contains unsupported characters.");
    }
    if (!HAS_LETTER.matcher(value).matches()) {
      throw new ApplicationException(
          "Checklist Name must contain at least one letter and cannot consist only of numbers,"
              + " spaces, or special characters.");
    }
    return value;
  }

  /** Trims and validates one Checklist Item, returning the value to persist. */
  public static String requireValidItem(String raw) {
    String value = trim(raw);
    if (value.isEmpty()) {
      throw new ApplicationException("Checklist Item is required.");
    }
    if (value.length() > ITEM_MAX_LENGTH) {
      throw new ApplicationException("Checklist Item cannot exceed 250 characters.");
    }
    if (!ITEM_ALLOWED.matcher(value).matches()) {
      throw new ApplicationException("Checklist Item contains unsupported characters.");
    }
    if (!HAS_LETTER.matcher(value).matches()) {
      throw new ApplicationException(
          "Checklist Item must contain at least one letter and cannot consist only of numbers,"
              + " spaces, or special characters.");
    }
    return value;
  }

  /** Leading and trailing spaces are stripped; spaces between words are preserved. */
  private static String trim(String raw) {
    return raw == null ? "" : raw.trim();
  }
}
