package com.netlink.onemep_feature.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification settings for project lifecycle/priority change emails (ONEMEP-14).
 *
 * @param enabled master switch; when false, notifications are skipped entirely
 * @param from the "From" address used for outbound notification mail
 */
@ConfigurationProperties(prefix = "feature.notifications")
public record NotificationProperties(boolean enabled, String from) {

  public NotificationProperties {
    if (from == null || from.isBlank()) {
      from = "no-reply@onemep.local";
    }
  }
}
