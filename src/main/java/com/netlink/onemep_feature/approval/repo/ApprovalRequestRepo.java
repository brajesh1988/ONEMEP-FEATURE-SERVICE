package com.netlink.onemep_feature.approval.repo;

import com.netlink.onemep_feature.approval.model.ApprovalRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRequestRepo extends JpaRepository<ApprovalRequest, Long> {

  /** The open request for a file, if there is one. At most one can exist. */
  @Query(
      """
      SELECT r FROM ApprovalRequest r
      WHERE r.file.id = :fileId
        AND r.status = com.netlink.onemep_feature.approval.model.ApprovalStatus.PENDING
      """)
  Optional<ApprovalRequest> findPendingForFile(@Param("fileId") Long fileId);

  /** The whole journey for a logical file, newest request first. */
  @Query("SELECT r FROM ApprovalRequest r WHERE r.file.id = :fileId ORDER BY r.id DESC")
  List<ApprovalRequest> findJourneyForFile(@Param("fileId") Long fileId);

  /** Most recent request for a file, used to decide whether the next one is a resubmission. */
  @Query("SELECT r FROM ApprovalRequest r WHERE r.file.id = :fileId ORDER BY r.id DESC LIMIT 1")
  Optional<ApprovalRequest> findLatestForFile(@Param("fileId") Long fileId);

  /**
   * Whether this revision already used up its one approval cycle. Recalled and cancelled requests
   * do not count — neither was a decision on the file.
   */
  @Query(
      """
      SELECT COUNT(r) > 0 FROM ApprovalRequest r
      WHERE r.version.id = :versionId
        AND r.status IN (
          com.netlink.onemep_feature.approval.model.ApprovalStatus.APPROVED,
          com.netlink.onemep_feature.approval.model.ApprovalStatus.EDIT_REQUESTED,
          com.netlink.onemep_feature.approval.model.ApprovalStatus.REJECTED)
      """)
  boolean hasCompletedCycle(@Param("versionId") Long versionId);

  @Query(
      """
      SELECT COUNT(r) > 0 FROM ApprovalRequest r
      WHERE r.file.id = :fileId
        AND r.status = com.netlink.onemep_feature.approval.model.ApprovalStatus.PENDING
      """)
  boolean hasPendingForFile(@Param("fileId") Long fileId);

  // ── Central listing (ONEMEP-44) ─────────────────────────────────────────────
  // Personalised: a request is relevant when the user raised it or was asked to review it.
  // Self-approval is forbidden, so the two relationships are mutually exclusive and a request can
  // never produce two rows for the same person.

  /**
   * Open requests relevant to the user. Only <em>active</em> assignments count — someone an admin
   * has reassigned away should stop seeing it as theirs to review.
   */
  @Query(
      """
      SELECT r FROM ApprovalRequest r
      WHERE r.status = com.netlink.onemep_feature.approval.model.ApprovalStatus.PENDING
        AND (r.requesterId = :userId
             OR EXISTS (SELECT 1 FROM ApprovalAssignee a
                        WHERE a.request = r AND a.userId = :userId AND a.active = true))
      ORDER BY r.createdDate DESC, r.id DESC
      """)
  Page<ApprovalRequest> findPendingForUser(@Param("userId") Long userId, Pageable pageable);

  /**
   * Closed requests the user was part of. Retired assignments are included here — they took part,
   * even if the task later moved on.
   */
  @Query(
      """
      SELECT r FROM ApprovalRequest r
      WHERE r.status <> com.netlink.onemep_feature.approval.model.ApprovalStatus.PENDING
        AND (r.requesterId = :userId
             OR EXISTS (SELECT 1 FROM ApprovalAssignee a
                        WHERE a.request = r AND a.userId = :userId))
      ORDER BY r.completedAt DESC, r.id DESC
      """)
  Page<ApprovalRequest> findCompletedForUser(@Param("userId") Long userId, Pageable pageable);

  /**
   * What the sidebar badge shows: requests genuinely waiting on <em>this</em> user. Deliberately
   * narrower than the Pending tab, which also carries requests they raised and are waiting on
   * somebody else — ONEMEP-44 calls out that the two numbers differ on purpose.
   */
  @Query(
      """
      SELECT COUNT(r) FROM ApprovalRequest r
      WHERE r.status = com.netlink.onemep_feature.approval.model.ApprovalStatus.PENDING
        AND EXISTS (SELECT 1 FROM ApprovalAssignee a
                    WHERE a.request = r
                      AND a.userId = :userId
                      AND a.active = true
                      AND a.stage = r.currentStage
                      AND a.decision = com.netlink.onemep_feature.approval.model.ApproverDecision.PENDING)
      """)
  long countAwaitingUser(@Param("userId") Long userId);
}
