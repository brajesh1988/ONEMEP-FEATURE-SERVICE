package com.netlink.onemep_feature.designimport.parser;

import java.util.List;
import java.util.Map;

/**
 * One data row, already mapped from physical columns to {@link ImportColumn}.
 *
 * @param rowNumber the number the user sees in Excel — the header is row 1, so the first data row
 *     is normally 2. Error messages quote this, so it must be the spreadsheet's own numbering and
 *     never a zero-based index into the data.
 * @param values trimmed cell text per recognised column; a column absent from the file, or blank in
 *     this row, is simply not a key
 */
public record SheetRow(int rowNumber, Map<ImportColumn, String> values) {

  public SheetRow {
    values = Map.copyOf(values);
  }

  /** Trimmed value, or {@code null} when the cell is absent or blank. */
  public String get(ImportColumn column) {
    return values.get(column);
  }

  public boolean has(ImportColumn column) {
    return values.containsKey(column);
  }

  /**
   * Whether the row carries nothing at all. Trailing empty rows are extremely common — a user
   * deletes the example data and leaves the formatting behind — and reporting a hundred "Title is
   * required" errors for rows the user considers empty would make the error list useless.
   */
  public boolean isBlank() {
    return values.isEmpty();
  }

  /** Builds a row from raw cell text, dropping blanks and unmapped columns. */
  public static SheetRow of(
      int rowNumber, Map<ImportColumn, Integer> headerMap, List<String> cells) {
    Map<ImportColumn, String> values = new java.util.EnumMap<>(ImportColumn.class);
    headerMap.forEach(
        (column, index) -> {
          if (index < cells.size()) {
            String raw = cells.get(index);
            String trimmed = raw == null ? "" : raw.trim();
            if (!trimmed.isEmpty()) {
              values.put(column, trimmed);
            }
          }
        });
    return new SheetRow(rowNumber, values);
  }
}
