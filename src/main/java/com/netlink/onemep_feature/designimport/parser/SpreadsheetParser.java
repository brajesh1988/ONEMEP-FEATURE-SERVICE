package com.netlink.onemep_feature.designimport.parser;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads a spreadsheet a row at a time (ONEMEP-35).
 *
 * <p><b>Streaming is the contract, not an optimisation.</b> The ceiling is 150 MB per file and
 * several may be in one batch; materialising a workbook into a {@code List<SheetRow>} would put
 * every row of every file on the heap at once. Implementations push rows to a {@link RowHandler}
 * and hold at most one row at a time.
 *
 * <p>The source is a {@link Path} rather than an {@code InputStream} for the same reason: POI's
 * {@code OPCPackage.open(InputStream)} spools the entire package into memory before the first row
 * is read, which defeats streaming at exactly the sizes that need it. The caller is responsible for
 * materialising the stored object as a local temporary file and removing it afterwards.
 */
public interface SpreadsheetParser {

  /** Receives the header and then each data row in file order. */
  interface RowHandler {

    /**
     * Called once, before any row.
     *
     * @param headerMap recognised columns and the physical index each was found at
     * @param unrecognised header cells that matched no known column — reported as information,
     *     never an error: extra columns are how people annotate their own copy of a template
     */
    void header(Map<ImportColumn, Integer> headerMap, List<String> unrecognised);

    /**
     * Called once per data row, in file order.
     *
     * @return {@code false} to stop reading — the parser then closes the source and returns
     *     normally, which is how the row cap is applied without reading the rest of the file
     */
    boolean row(SheetRow row);
  }

  /**
   * Parses {@code source}.
   *
   * @throws SpreadsheetFormatException if the file is not readable as this format, or has no header
   */
  void parse(Path source, RowHandler handler);
}
