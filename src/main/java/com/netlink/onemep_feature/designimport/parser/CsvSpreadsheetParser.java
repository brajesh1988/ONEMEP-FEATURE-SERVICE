package com.netlink.onemep_feature.designimport.parser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RFC 4180 CSV reader.
 *
 * <p>Hand-written rather than pulled in as a dependency: the whole grammar is quoting, doubled
 * quotes and embedded newlines, and it is parsed here character by character so a field containing
 * a line break — which a Design Title legitimately might, pasted out of a document — does not
 * silently split one Design across two rows.
 *
 * <p>Reading is character-driven rather than line-driven for exactly that reason, and the row
 * number it reports is the <em>record</em> number, so it still matches what the user sees when the
 * same file is opened in a spreadsheet application.
 *
 * <p>The delimiter is a comma, per RFC 4180 and the supplied template. Sniffing for a semicolon
 * variant was considered and rejected: the delimiter has to be known before quotes can be parsed,
 * so guessing it from an already-parsed line corrupts any field containing the other candidate. A
 * semicolon-delimited export is better rejected clearly than imported wrongly.
 */
public class CsvSpreadsheetParser implements SpreadsheetParser {

  private static final char DELIMITER = ',';
  private static final int BOM = '\uFEFF';

  @Override
  public void parse(Path source, RowHandler handler) {
    try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
      new Reading(reader, handler).run();
    } catch (java.nio.charset.CharacterCodingException e) {
      throw new SpreadsheetFormatException(
          "The file is not valid UTF-8 text and could not be read as a CSV.", e);
    } catch (IOException e) {
      throw new SpreadsheetFormatException(
          "The file could not be read. It may be corrupt or incomplete.", e);
    }
  }

  /** One pass over one file. Holds the parse position so the state machine stays readable. */
  private static final class Reading {

    private final Reader reader;
    private final RowHandler handler;

    private int pushback = NOTHING_PUSHED;

    private static final int NOTHING_PUSHED = -2;

    Reading(Reader reader, RowHandler handler) {
      this.reader = reader;
      this.handler = handler;
    }

    void run() throws IOException {
      List<String> header = nextRecord();
      if (header == null || header.stream().allMatch(cell -> cell.trim().isEmpty())) {
        throw new SpreadsheetFormatException(
            "The file is empty, or its first row is not a header row.");
      }

      Map<ImportColumn, Integer> headerMap = ImportColumn.mapHeaders(header);
      handler.header(headerMap, unrecognised(header));

      int rowNumber = 1;
      List<String> cells;
      while ((cells = nextRecord()) != null) {
        rowNumber++;
        if (!handler.row(SheetRow.of(rowNumber, headerMap, cells))) {
          return;
        }
      }
    }

    private static List<String> unrecognised(List<String> header) {
      return header.stream()
          .filter(cell -> cell != null && !cell.isBlank())
          .filter(cell -> ImportColumn.match(cell).isEmpty())
          .map(String::trim)
          .toList();
    }

    /**
     * Reads one record. Returns {@code null} at end of input.
     *
     * <p>A record is a list of fields; a bare newline ends it, a newline inside quotes does not.
     */
    private List<String> nextRecord() throws IOException {
      List<String> fields = new ArrayList<>();
      StringBuilder field = new StringBuilder();
      boolean inQuotes = false;
      boolean sawAnything = false;

      while (true) {
        int next = read();
        if (next == -1) {
          if (!sawAnything) {
            return null;
          }
          fields.add(field.toString());
          return fields;
        }

        char c = (char) next;
        sawAnything = true;

        if (inQuotes) {
          if (c == '"') {
            int peek = read();
            if (peek == '"') {
              field.append('"'); // a doubled quote is one literal quote
            } else {
              inQuotes = false;
              push(peek);
            }
          } else {
            field.append(c);
          }
          continue;
        }

        if (c == '"' && field.isEmpty()) {
          inQuotes = true;
        } else if (c == DELIMITER) {
          fields.add(field.toString());
          field.setLength(0);
        } else if (c == '\r') {
          int peek = read();
          if (peek != '\n') {
            push(peek);
          }
          fields.add(field.toString());
          return fields;
        } else if (c == '\n') {
          fields.add(field.toString());
          return fields;
        } else {
          field.append(c);
        }
      }
    }

    private int read() throws IOException {
      if (pushback != NOTHING_PUSHED) {
        int value = pushback;
        pushback = NOTHING_PUSHED;
        return value;
      }
      int value = reader.read();
      // A UTF-8 BOM would otherwise become part of the first header cell and stop it matching.
      if (value == BOM) {
        value = reader.read();
      }
      return value;
    }

    private void push(int value) {
      pushback = value;
    }
  }
}
