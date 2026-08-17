package com.netlink.onemep_feature.designimport.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSpreadsheetParserTest {

  @TempDir Path directory;

  private final CsvSpreadsheetParser parser = new CsvSpreadsheetParser();

  @Test
  void parse_withASimpleFile_reportsHeaderThenEachRow() throws IOException {
    Recorder recorder =
        parse(
            """
            Zone,Discipline,Type,Subject,Floor,Stage,Title
            Z01,M,SCH,CHW,00,DD,Chilled water schematic
            Z02,E,PLN,CHW,00,DD,Power layout
            """);

    assertThat(recorder.headerMap)
        .containsEntry(ImportColumn.ZONE, 0)
        .containsEntry(ImportColumn.TITLE, 6);
    assertThat(recorder.rows).hasSize(2);
    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE)).isEqualTo("Chilled water schematic");
    assertThat(recorder.rows.get(1).get(ImportColumn.DISCIPLINE)).isEqualTo("E");
  }

  /** The header is row 1, so errors quote the number the user sees in their spreadsheet. */
  @Test
  void parse_numbersRowsFromTheHeaderRow() throws IOException {
    Recorder recorder =
        parse(
            """
            Discipline,Title
            M,First
            M,Second
            """);

    assertThat(recorder.rows).extracting(SheetRow::rowNumber).containsExactly(2, 3);
  }

  @Test
  void parse_withAQuotedFieldContainingTheDelimiter_keepsItAsOneValue() throws IOException {
    Recorder recorder =
        parse(
            """
            Discipline,Title
            M,"Chilled water, risers and headers"
            """);

    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE))
        .isEqualTo("Chilled water, risers and headers");
  }

  @Test
  void parse_withADoubledQuoteInsideAQuotedField_unescapesIt() throws IOException {
    Recorder recorder =
        parse(
            """
            Discipline,Title
            M,"The ""main"" riser"
            """);

    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE)).isEqualTo("The \"main\" riser");
  }

  /** The reason this is a character-driven reader: a line break inside quotes is not a new row. */
  @Test
  void parse_withANewlineInsideAQuotedField_doesNotSplitTheRow() throws IOException {
    Recorder recorder = parse("Discipline,Title\nM,\"Line one\nLine two\"\nE,Next\n");

    assertThat(recorder.rows).hasSize(2);
    assertThat(recorder.rows.get(0).get(ImportColumn.TITLE)).isEqualTo("Line one\nLine two");
    assertThat(recorder.rows.get(1).rowNumber()).isEqualTo(3);
  }

  @Test
  void parse_withWindowsLineEndings_readsTheSameRows() throws IOException {
    Recorder recorder = parse("Discipline,Title\r\nM,First\r\nE,Second\r\n");

    assertThat(recorder.rows).hasSize(2);
    assertThat(recorder.rows.get(1).get(ImportColumn.TITLE)).isEqualTo("Second");
  }

  @Test
  void parse_withAByteOrderMark_stillMatchesTheFirstHeader() throws IOException {
    Recorder recorder = parse("﻿Discipline,Title\nM,First\n");

    assertThat(recorder.headerMap).containsEntry(ImportColumn.DISCIPLINE, 0);
  }

  @Test
  void parse_withAShortRow_leavesTheMissingColumnsAbsent() throws IOException {
    Recorder recorder = parse("Discipline,Type,Title\nM\n");

    SheetRow row = recorder.rows.get(0);
    assertThat(row.get(ImportColumn.DISCIPLINE)).isEqualTo("M");
    assertThat(row.has(ImportColumn.TITLE)).isFalse();
  }

  @Test
  void parse_withABlankRow_reportsItAsBlank() throws IOException {
    Recorder recorder = parse("Discipline,Title\nM,First\n,\n");

    assertThat(recorder.rows).hasSize(2);
    assertThat(recorder.rows.get(1).isBlank()).isTrue();
  }

  @Test
  void parse_withAnEmptyFile_isRejected() throws IOException {
    assertThatThrownBy(() -> parse(""))
        .isInstanceOf(SpreadsheetFormatException.class)
        .hasMessage("The file is empty, or its first row is not a header row.");
  }

  @Test
  void parse_stopsReadingWhenTheHandlerSaysSo() throws IOException {
    Path file = write("Discipline,Title\nM,One\nM,Two\nM,Three\n");
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

  @Test
  void parse_reportsUnrecognisedHeadersWithoutFailing() throws IOException {
    Recorder recorder = parse("Discipline,Cost Centre,Title\nM,X,First\n");

    assertThat(recorder.unrecognised).containsExactly("Cost Centre");
    assertThat(recorder.rows).hasSize(1);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Recorder parse(String content) throws IOException {
    Recorder recorder = new Recorder();
    parser.parse(write(content), recorder);
    return recorder;
  }

  private Path write(String content) throws IOException {
    Path file = directory.resolve("import-" + System.nanoTime() + ".csv");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static final class Recorder implements SpreadsheetParser.RowHandler {
    private final List<SheetRow> rows = new ArrayList<>();
    private Map<ImportColumn, Integer> headerMap = Map.of();
    private List<String> unrecognised = List.of();

    @Override
    public void header(Map<ImportColumn, Integer> headerMap, List<String> unrecognised) {
      this.headerMap = headerMap;
      this.unrecognised = unrecognised;
    }

    @Override
    public boolean row(SheetRow row) {
      rows.add(row);
      return true;
    }
  }
}
