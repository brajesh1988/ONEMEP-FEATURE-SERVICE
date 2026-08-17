package com.netlink.onemep_feature.designimport.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XlsxSpreadsheetParserTest {

  @TempDir Path directory;

  private final XlsxSpreadsheetParser parser = new XlsxSpreadsheetParser();

  @Test
  void parse_withASimpleWorkbook_reportsHeaderThenEachRow() throws IOException {
    Path file =
        workbook(
            List.of("Zone", "Discipline", "Type", "Subject", "Floor", "Stage", "Title"),
            List.of("Z01", "M", "SCH", "CHW", "00", "DD", "Chilled water schematic"),
            List.of("Z02", "E", "PLN", "CHW", "00", "DD", "Power layout"));

    Recorder recorder = parse(file);

    assertThat(recorder.headerMap)
        .containsEntry(ImportColumn.ZONE, 0)
        .containsEntry(ImportColumn.TITLE, 6);
    assertThat(recorder.rows).hasSize(2);
    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE)).isEqualTo("Chilled water schematic");
  }

  @Test
  void parse_numbersRowsFromTheHeaderRow() throws IOException {
    Path file =
        workbook(List.of("Discipline", "Title"), List.of("M", "First"), List.of("E", "Second"));

    assertThat(parse(file).rows).extracting(SheetRow::rowNumber).containsExactly(2, 3);
  }

  /**
   * POI omits blank cells entirely. Placing values by arrival order rather than by their column
   * reference would shift every value after a gap one column to the left.
   */
  @Test
  void parse_withAGapInTheMiddleOfARow_keepsLaterValuesInTheirOwnColumns() throws IOException {
    Path file = directory.resolve("gapped.xlsx");
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Designs");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Zone");
      header.createCell(1).setCellValue("Discipline");
      header.createCell(2).setCellValue("Title");

      Row row = sheet.createRow(1);
      row.createCell(0).setCellValue("Z01");
      // column 1 deliberately never created — POI omits it from the sheet XML entirely
      row.createCell(2).setCellValue("Riser diagram");

      try (OutputStream out = Files.newOutputStream(file)) {
        wb.write(out);
      }
    }

    SheetRow parsed = parse(file).rows.get(0);
    assertThat(parsed.get(ImportColumn.ZONE)).isEqualTo("Z01");
    assertThat(parsed.has(ImportColumn.DISCIPLINE)).isFalse();
    assertThat(parsed.get(ImportColumn.TITLE)).isEqualTo("Riser diagram");
  }

  @Test
  void parse_readsOnlyTheFirstWorksheet() throws IOException {
    Path file = directory.resolve("two-sheets.xlsx");
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet first = wb.createSheet("Designs");
      Row header = first.createRow(0);
      header.createCell(0).setCellValue("Discipline");
      header.createCell(1).setCellValue("Title");
      Row data = first.createRow(1);
      data.createCell(0).setCellValue("M");
      data.createCell(1).setCellValue("Only this one");

      Sheet second = wb.createSheet("Notes");
      second.createRow(0).createCell(0).setCellValue("Ignore me");

      try (OutputStream out = Files.newOutputStream(file)) {
        wb.write(out);
      }
    }

    Recorder recorder = parse(file);
    assertThat(recorder.rows).hasSize(1);
    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE)).isEqualTo("Only this one");
  }

  @Test
  void parse_withAFileThatIsNotAWorkbook_isRejectedWithReadableWording() throws IOException {
    Path file = directory.resolve("actually-a-csv.xlsx");
    Files.writeString(file, "Discipline,Title\nM,First\n", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parse(file))
        .isInstanceOf(SpreadsheetFormatException.class)
        .hasMessageContaining("could not be read as an Excel workbook");
  }

  @Test
  void parse_withTruncatedBytes_isRejectedRatherThanCrashing() throws IOException {
    Path full = workbook(List.of("Discipline", "Title"), List.of("M", "First"));
    byte[] bytes = Files.readAllBytes(full);
    Path truncated = directory.resolve("truncated.xlsx");
    Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length / 2));

    assertThatThrownBy(() -> parse(truncated)).isInstanceOf(SpreadsheetFormatException.class);
  }

  @Test
  void parse_withAnEmptyWorkbook_isRejected() throws IOException {
    Path file = directory.resolve("empty.xlsx");
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      wb.createSheet("Designs");
      try (OutputStream out = Files.newOutputStream(file)) {
        wb.write(out);
      }
    }

    assertThatThrownBy(() -> parse(file))
        .isInstanceOf(SpreadsheetFormatException.class)
        .hasMessage("The file is empty, or its first row is not a header row.");
  }

  @Test
  void parse_stopsReadingWhenTheHandlerSaysSo() throws IOException {
    Path file =
        workbook(
            List.of("Discipline", "Title"),
            List.of("M", "One"),
            List.of("M", "Two"),
            List.of("M", "Three"));
    List<SheetRow> seen = new ArrayList<>();

    parser.parse(
        file,
        new SpreadsheetParser.RowHandler() {
          @Override
          public void header(Map<ImportColumn, Integer> headerMap, List<String> unrecognised) {}

          @Override
          public boolean row(SheetRow row) {
            seen.add(row);
            return seen.size() < 2;
          }
        });

    assertThat(seen).hasSize(2);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  @SafeVarargs
  private Path workbook(List<String> header, List<String>... rows) throws IOException {
    WorkbookBuilder builder = new WorkbookBuilder().header(header.toArray(new String[0]));
    for (List<String> row : rows) {
      builder.row(row.toArray(new String[0]));
    }
    return builder.build(directory);
  }

  private Recorder parse(Path file) {
    Recorder recorder = new Recorder();
    parser.parse(file, recorder);
    return recorder;
  }

  /** Small builder so a test reads as its data rather than as POI calls. */
  static final class WorkbookBuilder {
    private final List<String[]> rows = new ArrayList<>();

    WorkbookBuilder header(String... cells) {
      rows.add(cells);
      return this;
    }

    WorkbookBuilder row(String... cells) {
      rows.add(cells);
      return this;
    }

    Path build(Path directory) throws IOException {
      Path file = directory.resolve("import-" + System.nanoTime() + ".xlsx");
      try (XSSFWorkbook wb = new XSSFWorkbook()) {
        Sheet sheet = wb.createSheet("Designs");
        for (int r = 0; r < rows.size(); r++) {
          Row row = sheet.createRow(r);
          String[] cells = rows.get(r);
          for (int c = 0; c < cells.length; c++) {
            row.createCell(c).setCellValue(cells[c]);
          }
        }
        try (OutputStream out = Files.newOutputStream(file)) {
          wb.write(out);
        }
      }
      return file;
    }
  }

  private static final class Recorder implements SpreadsheetParser.RowHandler {
    private final List<SheetRow> rows = new ArrayList<>();
    private Map<ImportColumn, Integer> headerMap = Map.of();

    @Override
    public void header(Map<ImportColumn, Integer> headerMap, List<String> unrecognised) {
      this.headerMap = headerMap;
    }

    @Override
    public boolean row(SheetRow row) {
      rows.add(row);
      return true;
    }
  }
}
