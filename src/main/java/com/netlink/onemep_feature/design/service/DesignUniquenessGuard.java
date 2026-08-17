package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The two Design duplicate rules, in one place because two features enforce them: Add Design
 * (ONEMEP-36) and the spreadsheet importer (ONEMEP-35).
 *
 * <p>They are <b>two independent per-Project rules, never a composite key</b>:
 *
 * <ul>
 *   <li>same Design Number, different Title — rejected, duplicate number;
 *   <li>different Design Number, same Title — rejected, duplicate title;
 *   <li>same and same — rejected by whichever runs first;
 *   <li>different and different — valid.
 * </ul>
 *
 * <p>So callers run both checks and may collect two failures for one candidate. The importer relies
 * on that: ONEMEP-35 shows a row reported against each rule separately, with its own message.
 *
 * <p>Backed by {@code uq_design_number} and {@code uq_design_title} (V24). These checks are the
 * friendly path — they produce the ticket's wording — but they are not the enforcement. Two
 * concurrent inserts can both pass here and only one can pass the database, which is the point of
 * having the constraints.
 */
@Component
@RequiredArgsConstructor
public class DesignUniquenessGuard {

  private final DesignRepo designRepo;

  /**
   * ONEMEP-35: {@code "Design Number 'X' already exists in this Project."} — rejected even when the
   * Title differs.
   *
   * @param excludeId the record being edited, so an update does not collide with itself
   */
  public void requireUniqueDesignNumber(Long projectId, String designNumber, Long excludeId) {
    designRepo
        .findByDesignNumber(projectId, designNumber, excludeId)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(duplicateNumberMessage(designNumber));
            });
  }

  /**
   * ONEMEP-35: {@code "A Design with this Title already exists in this Project."} — rejected even
   * when the Design Number differs.
   */
  public void requireUniqueTitle(Long projectId, String titleNormalized, Long excludeId) {
    designRepo
        .findByTitle(projectId, titleNormalized, excludeId)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(duplicateTitleMessage());
            });
  }

  /**
   * The message text on its own. The importer reports row failures rather than throwing, so it
   * needs the wording without the exception — and taking it from here is what keeps an imported
   * duplicate and a manually created one saying exactly the same thing.
   */
  public static String duplicateNumberMessage(String designNumber) {
    return "Design Number '" + designNumber + "' already exists in this Project.";
  }

  public static String duplicateTitleMessage() {
    return "A Design with this Title already exists in this Project.";
  }
}
