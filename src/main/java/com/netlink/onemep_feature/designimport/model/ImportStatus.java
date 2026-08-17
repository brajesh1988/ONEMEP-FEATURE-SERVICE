package com.netlink.onemep_feature.designimport.model;

/**
 * Progress of one uploaded file, and of the batch as a whole (ONEMEP-35).
 *
 * <p>The ticket lists these states per file; the same set describes the batch, which is simply the
 * aggregate of its files, so one enum serves both rather than two that would have to be kept in
 * step.
 *
 * <p>Constants are the stable machine codes stored in the database and matched by {@code
 * ck_design_import_batch_status}. {@link #label()} carries the display wording — the ticket's
 * "Completed with errors" is not a legal identifier, and the two must be free to diverge without a
 * migration.
 */
public enum ImportStatus {

  /** Accepted and queued; nothing has been read yet. */
  READY("Ready"),

  /** Bytes are being written to storage. */
  UPLOADING("Uploading"),

  /** Being parsed and validated. */
  PROCESSING("Processing"),

  /** Finished, every row imported. */
  IMPORTED("Imported"),

  /** Finished, some rows imported and some rejected — the partial-success outcome. */
  COMPLETED_WITH_ERRORS("Completed with errors"),

  /** Nothing could be imported: the file was unreadable, or every row was rejected. */
  FAILED("Failed");

  private final String label;

  ImportStatus(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** Whether no further transition is expected. */
  public boolean isTerminal() {
    return this == IMPORTED || this == COMPLETED_WITH_ERRORS || this == FAILED;
  }
}
