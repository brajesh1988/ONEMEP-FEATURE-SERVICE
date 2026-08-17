package com.netlink.onemep_feature.approval.dto;

import com.netlink.onemep_feature.approval.model.ApprovalStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for the central Approval Listing (ONEMEP-44). */
public final class ApprovalListingDto {
  private ApprovalListingDto() {}

  /** Which tab a request belongs to. Derived from status, never stored. */
  public enum Tab {
    PENDING,
    COMPLETED;

    public static Tab from(String raw) {
      return raw != null && "COMPLETED".equalsIgnoreCase(raw.trim()) ? COMPLETED : PENDING;
    }
  }

  /**
   * The signed-in user's relationship to a request. Mutually exclusive — self-approval is barred.
   */
  public enum Relationship {
    /** Assigned to them for review. */
    TO_REVIEW,

    /** They raised it. */
    YOUR_REQUEST
  }

  /**
   * One row of the listing.
   *
   * @param revisionLabel the revision <em>this request</em> was raised against, which stays correct
   *     even after newer revisions are uploaded
   * @param counterparty the requester when reviewing, or the approvers when it is your own request
   * @param statusLabel display form — "Pending with Priya Nair", "1 of 2 Approved", "Approved"
   * @param date raised date on the Pending tab, completion date on Completed
   */
  public record Row(
      Long approvalRequestId,
      Long designId,
      String designNumber,
      String title,
      Long fileId,
      String fileName,
      String fileExtension,
      Long versionId,
      String revisionLabel,
      Long projectId,
      String projectNumber,
      String projectName,
      Relationship role,
      String counterparty,
      ApprovalStatus status,
      String statusLabel,
      LocalDateTime date) {}

  /**
   * Tab counts plus the sidebar badge.
   *
   * @param actionRequiredCount requests waiting on this user specifically — deliberately smaller
   *     than {@code pendingCount}, which also includes requests they raised and are waiting on
   *     someone else
   */
  public record Summary(long actionRequiredCount, long pendingCount, long completedCount) {}

  public record Page(
      List<Row> content, long totalElements, int totalPages, int pageNumber, int pageSize) {}
}
