package com.netlink.onemep_feature.designimport.service;

/**
 * The sentences the importer reports its outcome with (ONEMEP-35).
 *
 * <p>Gathered in one place because they are the visible product of the whole feature — the ticket
 * quotes {@code "42 of 50 Designs imported. 8 rows require correction."} as an acceptance
 * criterion, so the wording is specified behaviour and is unit-tested as such rather than being
 * scattered through the processor as string concatenation.
 */
public final class ImportSummary {
  private ImportSummary() {}

  /**
   * Outcome of a completed batch or file.
   *
   * @param imported rows that became Designs
   * @param total rows considered — imported plus rejected, excluding blank ones
   */
  public static String of(int imported, int total) {
    if (total == 0) {
      return "No Design rows were found.";
    }
    if (imported == total) {
      return imported == 1 ? "1 Design imported." : imported + " Designs imported.";
    }
    if (imported == 0) {
      return "No Designs were imported. " + correction(total);
    }
    return imported + " of " + total + " Designs imported. " + correction(total - imported);
  }

  private static String correction(int failed) {
    return failed == 1 ? "1 row requires correction." : failed + " rows require correction.";
  }

  /** Shown while the batch is still running, so a poll before completion still reads sensibly. */
  public static String inProgress() {
    return "Import in progress.";
  }

  /** A file that could not be read at all contributes no rows, only its own message. */
  public static String unreadable() {
    return "The file could not be read and no rows were imported.";
  }
}
