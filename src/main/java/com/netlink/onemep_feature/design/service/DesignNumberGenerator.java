package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.exception.ApplicationException;
import java.util.regex.Pattern;

/**
 * Builds the Design Number from its segments (ONEMEP-36).
 *
 * <pre>
 * ONEMEP-&lt;ProjectCode&gt;-&lt;Zone&gt;-&lt;Disc&gt;-&lt;Type&gt;-&lt;Subject&gt;-&lt;Floor&gt;-&lt;Stage&gt;
 * </pre>
 *
 * <p>Segment order is fixed regardless of the order the user filled the form in, and the configured
 * <em>codes</em> are used rather than the labels.
 */
public final class DesignNumberGenerator {
  private DesignNumberGenerator() {}

  public static final String PREFIX = "ONEMEP";

  /** Stand-in for an omitted optional segment, so a number never has an empty position. */
  public static final String EMPTY_SEGMENT = "XX";

  private static final Pattern ZONE_ALLOWED = Pattern.compile("^[A-Za-z0-9]{1,10}$");

  /**
   * Normalises a typed Zone: trimmed, upper-cased, and {@code XX} when blank. ONEMEP-36 describes
   * Zone as entered rather than selected, which is why it is not a catalogue value.
   */
  public static String normalizeZone(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase();
    if (value.isEmpty()) {
      return EMPTY_SEGMENT;
    }
    if (!ZONE_ALLOWED.matcher(value).matches()) {
      throw new ApplicationException(
          "Zone may contain only letters and digits, up to 10 characters.");
    }
    return value;
  }

  /**
   * @param projectCode the Project's number, e.g. {@code 40012}
   * @throws ApplicationException if the project has no code — ONEMEP-36 blocks creation outright
   *     rather than generating a malformed number
   */
  public static String generate(
      String projectCode,
      String zoneCode,
      String disciplineCode,
      String typeCode,
      String subjectCode,
      String floorCode,
      String stageCode) {

    if (projectCode == null || projectCode.isBlank()) {
      throw new ApplicationException(
          "This Project does not have a valid Project Code. A Design cannot be created.");
    }

    return String.join(
        "-",
        PREFIX,
        projectCode.trim(),
        segment(zoneCode),
        segment(disciplineCode),
        segment(typeCode),
        segment(subjectCode),
        segment(floorCode),
        segment(stageCode));
  }

  private static String segment(String code) {
    return code == null || code.isBlank() ? EMPTY_SEGMENT : code.trim();
  }
}
