package com.netlink.onemep_feature.designimport.parser;

/**
 * The file cannot be read as a spreadsheet at all, or can be read but has no usable header.
 *
 * <p>Distinct from a row failing validation: a row error leaves the rest of the file importable,
 * whereas this fails the whole file. The message is written to be shown to the user as the file's
 * status message, so it must never carry a parser stack trace or a library's internal wording.
 *
 * <p>Not an {@code ApplicationException}: nothing here happens on a request thread, so there is no
 * HTTP status to map to. It is caught by the processor and recorded against the file.
 */
public class SpreadsheetFormatException extends RuntimeException {

  public SpreadsheetFormatException(String message) {
    super(message);
  }

  public SpreadsheetFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
