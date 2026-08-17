package com.netlink.onemep_feature.designimport.parser;

import java.util.Locale;
import java.util.Set;

/**
 * Chooses a parser by file extension, and owns the list of formats the importer accepts.
 *
 * <p>The extension is the only signal available at submission time — the bytes have not been read
 * yet, and are not going to be on a request thread. A file whose extension lies is caught later by
 * the parser itself, which fails that one file rather than the batch.
 */
public final class SpreadsheetParsers {
  private SpreadsheetParsers() {}

  public static final String XLSX = "xlsx";
  public static final String CSV = "csv";

  /** ONEMEP-35 names exactly these two. {@code .xls} is deliberately not accepted. */
  public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(XLSX, CSV);

  public static boolean isSupported(String extension) {
    return extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
  }

  public static SpreadsheetParser forExtension(String extension) {
    String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case XLSX -> new XlsxSpreadsheetParser();
      case CSV -> new CsvSpreadsheetParser();
      default ->
          throw new SpreadsheetFormatException(
              "Only .xlsx and .csv files can be imported. '" + extension + "' is not supported.");
    };
  }
}
