package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.designimport.parser.ImportColumn;

/**
 * One reason one row cannot be imported, in the wording ONEMEP-35 specifies.
 *
 * <p>The ticket uses two message shapes and both are reproduced here rather than normalised into
 * one:
 *
 * <ul>
 *   <li>a problem with a single row reads {@code "Row 14 — <what is wrong>."};
 *   <li>a collision <em>between</em> two rows reads {@code "Rows 12 and 18 contain the same Design
 *       Number."} — it already names both rows, so prefixing it with one of them would be both
 *       redundant and misleading about which row is at fault.
 * </ul>
 *
 * @param rowNumber the row this is filed against — for a collision, the later of the two
 * @param column the offending column, or {@code null} for rules that span the whole row
 */
public record RowProblem(int rowNumber, ImportColumn column, String message) {

  static final String ROW_PREFIX = "Row ";
  static final String SEPARATOR = " — ";

  /** A problem with one column of one row. */
  public static RowProblem at(int rowNumber, ImportColumn column, String detail) {
    return new RowProblem(rowNumber, column, ROW_PREFIX + rowNumber + SEPARATOR + detail);
  }

  /** A problem with one row that belongs to no single column. */
  public static RowProblem at(int rowNumber, String detail) {
    return new RowProblem(rowNumber, null, ROW_PREFIX + rowNumber + SEPARATOR + detail);
  }

  /** A message that already names its own rows and must not be prefixed. */
  public static RowProblem verbatim(int rowNumber, String message) {
    return new RowProblem(rowNumber, null, message);
  }

  /** Column name as stored and displayed; {@code null} stays null rather than becoming "null". */
  public String columnName() {
    return column == null ? null : column.header();
  }
}
