package com.netlink.onemep_feature.designimport.parser;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Streaming {@code .xlsx} reader, built on POI's event model rather than {@code XSSFWorkbook}.
 *
 * <p>{@code XSSFWorkbook} builds the whole sheet as an object graph before the first row can be
 * read — comfortably an order of magnitude larger than the file on disk. At the 150 MB ceiling
 * ONEMEP-35 allows, and with several files potentially in one batch, that is a reliable way to
 * exhaust the heap. {@link XSSFReader} pulls the sheet XML through SAX instead, so memory stays
 * proportional to one row.
 *
 * <p>Only the <b>first</b> worksheet is read. The ticket describes one sheet of Designs; silently
 * importing whatever happens to be on sheet 2 would be worse than ignoring it.
 *
 * <p>Cell text comes from {@link DataFormatter}, which applies the cell's own display format. That
 * matters for codes: a Floor typed as {@code 00} into an unformatted cell is stored by Excel as the
 * number 0 and reads back as {@code "0"}. Nothing here can recover the leading zero — the
 * information is genuinely not in the file — so the row fails later with {@code "Floor '0' is not
 * configured."}, which at least names the real problem.
 */
public class XlsxSpreadsheetParser implements SpreadsheetParser {

  @Override
  public void parse(Path source, RowHandler handler) {
    try (OPCPackage pkg = OPCPackage.open(source.toFile(), PackageAccess.READ)) {
      ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
      XSSFReader reader = new XSSFReader(pkg);
      StylesTable styles = reader.getStylesTable();

      Iterator<InputStream> sheets = reader.getSheetsData();
      if (!sheets.hasNext()) {
        throw new SpreadsheetFormatException("The workbook contains no worksheets.");
      }

      try (InputStream sheet = sheets.next()) {
        SheetCollector collector = new SheetCollector(handler);
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(
            new XSSFSheetXMLHandler(styles, strings, collector, new DataFormatter(), false));
        try {
          parser.parse(new InputSource(sheet));
        } catch (StopReading stop) {
          // The handler asked to stop — the row cap. Not an error.
        }
        collector.requireHeaderSeen();
      }
    } catch (SpreadsheetFormatException e) {
      throw e;
    } catch (Exception e) {
      // POI throws a wide and unstable set of types for a file that is not a valid workbook —
      // including a plain IllegalArgumentException for a renamed .csv. They all mean the same
      // thing to the user.
      throw new SpreadsheetFormatException(
          "The file could not be read as an Excel workbook. It may be corrupt, password-protected,"
              + " or saved in an older .xls format.",
          e);
    }
  }

  /** Thrown to unwind out of SAX when the handler has seen enough rows. */
  private static final class StopReading extends RuntimeException {
    StopReading() {
      super(null, null, false, false); // no stack trace — this is control flow, not a failure
    }
  }

  /**
   * Turns POI's cell-at-a-time callbacks into whole rows.
   *
   * <p>POI omits blank cells entirely, so cells are placed by the column index decoded from their
   * reference rather than by arrival order — otherwise a gap in the middle of a row would shift
   * every value after it one column to the left.
   */
  private static final class SheetCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

    private final RowHandler handler;
    private final List<String> cells = new ArrayList<>();

    private Map<ImportColumn, Integer> headerMap;
    private boolean headerSeen;
    private int rowNumber;

    SheetCollector(RowHandler handler) {
      this.handler = handler;
    }

    @Override
    public void startRow(int rowNum) {
      cells.clear();
      rowNumber = rowNum + 1; // POI counts from 0; the user's spreadsheet counts from 1
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
      int index =
          cellReference == null
              ? cells.size()
              : new CellReference(cellReference).getCol(); // absolute column, gaps included
      while (cells.size() <= index) {
        cells.add("");
      }
      cells.set(index, formattedValue == null ? "" : formattedValue);
    }

    @Override
    public void endRow(int rowNum) {
      if (!headerSeen) {
        if (cells.stream().allMatch(cell -> cell == null || cell.isBlank())) {
          return; // skip blank leading rows before the header
        }
        headerSeen = true;
        headerMap = ImportColumn.mapHeaders(cells);
        handler.header(headerMap, unrecognised(cells));
        return;
      }

      if (!handler.row(SheetRow.of(rowNumber, headerMap, List.copyOf(cells)))) {
        throw new StopReading();
      }
    }

    void requireHeaderSeen() {
      if (!headerSeen) {
        throw new SpreadsheetFormatException(
            "The file is empty, or its first row is not a header row.");
      }
    }

    private static List<String> unrecognised(List<String> header) {
      return header.stream()
          .filter(cell -> cell != null && !cell.isBlank())
          .filter(cell -> ImportColumn.match(cell).isEmpty())
          .map(String::trim)
          .toList();
    }
  }
}
