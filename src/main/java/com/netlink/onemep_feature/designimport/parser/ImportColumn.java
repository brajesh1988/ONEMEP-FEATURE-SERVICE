package com.netlink.onemep_feature.designimport.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The columns the Design import spreadsheet may carry (ONEMEP-35).
 *
 * <p>The set mirrors {@code DesignDto.CreateRequest} exactly, because an imported row and a row
 * typed into Add Design must be able to express the same Design — anything the importer accepted
 * that Add Design could not would be a second, undocumented way to create a record.
 *
 * <p>Segment columns carry catalogue <em>codes</em> ({@code M}, {@code SCH}, {@code CHW}), not
 * labels and not ids. A spreadsheet a human fills in cannot be expected to know database ids, and
 * the codes are what the Design Number is built from anyway.
 */
public enum ImportColumn {
  ZONE("Zone", false),
  DISCIPLINE("Discipline", true),
  TYPE("Type", true),
  SUBJECT("Subject", true),
  FLOOR("Floor", true),
  STAGE("Stage", true),
  TITLE("Title", true, "Design Title"),
  SHEET_SIZE("Sheet Size", false),
  SCALE("Scale", false),
  PREPARED_BY("Prepared By", false),
  WORK_PROGRESS("Work Progress", false, "Progress");

  private final String header;
  private final boolean required;
  private final List<String> aliases;

  ImportColumn(String header, boolean required, String... aliases) {
    this.header = header;
    this.required = required;
    this.aliases = List.of(aliases);
  }

  /** The header as it appears in the supplied template, and as error messages name it. */
  public String header() {
    return header;
  }

  /** Whether a file missing this column can be processed at all. */
  public boolean required() {
    return required;
  }

  /**
   * Matches a header cell to a column.
   *
   * <p>Comparison is on the normalised form, so {@code " Sheet Size "}, {@code "SHEET SIZE"} and a
   * header carrying a non-breaking space all resolve to the same column. Spreadsheets that have
   * been through a copy-paste or an export do this constantly, and failing a 40 MB file because a
   * header had two spaces in it would be a poor use of the user's afternoon.
   */
  public static Optional<ImportColumn> match(String rawHeader) {
    String normalized = normalizeHeader(rawHeader);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    for (ImportColumn column : values()) {
      if (normalizeHeader(column.header).equals(normalized)) {
        return Optional.of(column);
      }
      for (String alias : column.aliases) {
        if (normalizeHeader(alias).equals(normalized)) {
          return Optional.of(column);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Trims, folds every run of whitespace — including the non-breaking space Excel exports leave
   * behind — to one plain space, and lower-cases.
   */
  public static String normalizeHeader(String raw) {
    if (raw == null) {
      return "";
    }
    // Java's \s matches neither a non-breaking space nor a zero-width BOM, and both arrive
    // routinely from Excel exports — so they are folded to a plain space before the collapse.
    return raw.replace('\u00A0', ' ')
        .replace('\uFEFF', ' ')
        .replaceAll("\\s+", " ")
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  /** The columns a file must contain, in template order — used to name what is missing. */
  public static List<ImportColumn> requiredColumns() {
    return List.of(values()).stream().filter(ImportColumn::required).toList();
  }

  /** Header row of the download template, in declaration order. */
  public static List<String> templateHeaders() {
    return List.of(values()).stream().map(ImportColumn::header).toList();
  }

  /** Maps each recognised column to the physical column index it was found at. */
  public static Map<ImportColumn, Integer> mapHeaders(List<String> headerCells) {
    Map<ImportColumn, Integer> mapped = new LinkedHashMap<>();
    for (int index = 0; index < headerCells.size(); index++) {
      final int position = index;
      // First occurrence wins: a duplicated header column is ignored rather than silently
      // overriding the data the user is more likely to have filled in.
      match(headerCells.get(index)).ifPresent(column -> mapped.putIfAbsent(column, position));
    }
    return mapped;
  }
}
