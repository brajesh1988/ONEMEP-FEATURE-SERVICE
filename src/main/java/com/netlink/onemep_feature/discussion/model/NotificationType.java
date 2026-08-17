package com.netlink.onemep_feature.discussion.model;

/**
 * Why a notification exists.
 *
 * <p>Only mentions raise one today. Approval notifications (ONEMEP-40) belong here too when they
 * are wired up — the table was shaped to take them without a second mechanism.
 */
public enum NotificationType {
  DISCUSSION_MENTION
}
