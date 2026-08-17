package com.netlink.onemep_feature.designimport.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers every Design Number and Title already claimed by an earlier row <em>in this batch</em>
 * (ONEMEP-35).
 *
 * <p>The database constraints cannot do this job on their own. Rows are imported one at a time, so
 * by the time row 18 hits the database row 12 has already committed and row 18 fails with the
 * ordinary "already exists in this Project" message — which is true but unhelpful, because the
 * conflict is with something the user just submitted and can still fix in the spreadsheet. The
 * ticket asks for the more useful wording, and that requires knowing what earlier rows claimed.
 *
 * <p><b>Scope is the whole batch, not one file.</b> Two files uploaded together are one submission
 * as far as the user is concerned, so a Design Number appearing in both is a collision and reads
 * {@code "Rows 12 of 'zone-a.xlsx' and 18 contain the same Design Number."}
 *
 * <p>The two rules stay independent throughout: a row colliding on the number and a row colliding
 * on the title are separate lookups producing separate messages, and one row can trip both.
 *
 * <p>Not thread-safe, and deliberately so — a batch is processed by one thread from start to
 * finish, which is also what makes "rows earlier in the batch" a well-defined idea.
 */
public class BatchDuplicateIndex {

  /** Where a value was first seen. */
  public record Claim(int rowNumber, String filename) {}

  private final Map<String, Claim> byDesignNumber = new HashMap<>();
  private final Map<String, Claim> byTitle = new HashMap<>();

  /** The file currently being read; claims record it so cross-file collisions can name it. */
  private String currentFilename = "";

  public void enterFile(String filename) {
    this.currentFilename = filename == null ? "" : filename;
  }

  /** An earlier claim on this Design Number, if any. Case-insensitive, like the database. */
  public Optional<Claim> findDesignNumber(String designNumber) {
    return Optional.ofNullable(byDesignNumber.get(normalize(designNumber)));
  }

  /** An earlier claim on this Title. The value passed in is already the normalised form. */
  public Optional<Claim> findTitle(String titleNormalized) {
    return Optional.ofNullable(byTitle.get(normalize(titleNormalized)));
  }

  /**
   * Records a row's claims.
   *
   * <p>Called only for rows that actually imported. A row rejected for some unrelated reason never
   * existed as far as the register is concerned, so it must not make a later row look like a
   * duplicate of it.
   */
  public void claim(int rowNumber, String designNumber, String titleNormalized) {
    Claim claim = new Claim(rowNumber, currentFilename);
    byDesignNumber.putIfAbsent(normalize(designNumber), claim);
    byTitle.putIfAbsent(normalize(titleNormalized), claim);
  }

  /**
   * ONEMEP-35's wording for a within-batch collision. Names the other file only when it is a
   * different one, so the common single-file case stays exactly as the ticket writes it.
   */
  public String collisionMessage(Claim earlier, int currentRow, String subject) {
    String where =
        earlier.filename().isEmpty() || earlier.filename().equals(currentFilename)
            ? ""
            : " of '" + earlier.filename() + "'";
    return "Rows "
        + earlier.rowNumber()
        + where
        + " and "
        + currentRow
        + " contain the same "
        + subject
        + ".";
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
