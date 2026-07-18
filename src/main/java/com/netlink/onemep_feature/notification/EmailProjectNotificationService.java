package com.netlink.onemep_feature.notification;

import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.user.model.UserAccountRef;
import com.netlink.onemep_feature.user.repo.UserAccountRefRepo;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email-backed implementation of {@link ProjectNotificationService}.
 *
 * <p>Delivery is best-effort: if notifications are disabled, no mail sender is configured, or the
 * SMTP call fails, the change is logged and swallowed so the originating request still succeeds.
 * The {@link JavaMailSender} is optional ({@link ObjectProvider}) because it is only
 * auto-configured when {@code spring.mail.host} is set.
 */
@Service
@Slf4j
public class EmailProjectNotificationService implements ProjectNotificationService {

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final UserAccountRefRepo userRepo;
  private final NotificationProperties properties;
  private final String mailHost;

  public EmailProjectNotificationService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      UserAccountRefRepo userRepo,
      NotificationProperties properties,
      @Value("${spring.mail.host:}") String mailHost) {
    this.mailSenderProvider = mailSenderProvider;
    this.userRepo = userRepo;
    this.properties = properties;
    this.mailHost = mailHost;
  }

  @Override
  public void notifyLifecycleChanged(
      ProjectMaster project, String oldStatus, String newStatus, List<Long> leadUserIds) {
    String subject =
        String.format("[%s] Lifecycle changed: %s", project.getProjectNumber(), newStatus);
    String body =
        String.format(
            "Project \"%s\" (%s) lifecycle status changed from %s to %s.",
            project.getName(), project.getProjectNumber(), oldStatus, newStatus);
    send(subject, body, leadUserIds);
  }

  @Override
  public void notifyPriorityChanged(
      ProjectMaster project, String oldPriority, String newPriority, List<Long> leadUserIds) {
    String subject =
        String.format("[%s] Priority changed: %s", project.getProjectNumber(), newPriority);
    String body =
        String.format(
            "Project \"%s\" (%s) priority changed from %s to %s.",
            project.getName(), project.getProjectNumber(), oldPriority, newPriority);
    send(subject, body, leadUserIds);
  }

  private void send(String subject, String body, List<Long> leadUserIds) {
    if (!properties.enabled()) {
      log.debug("Notifications disabled; skipping: {}", subject);
      return;
    }
    if (leadUserIds == null || leadUserIds.isEmpty()) {
      log.debug("No lead recipients; skipping: {}", subject);
      return;
    }
    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null || mailHost == null || mailHost.isBlank()) {
      log.info("No SMTP host configured (spring.mail.host); skipping notification: {}", subject);
      return;
    }
    String[] recipients =
        userRepo.findByIdIn(leadUserIds).stream()
            .map(UserAccountRef::getEmail)
            .filter(email -> email != null && !email.isBlank())
            .distinct()
            .toArray(String[]::new);
    if (recipients.length == 0) {
      log.warn("No resolvable lead emails; skipping: {}", subject);
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.from());
      message.setTo(recipients);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
      log.info("Sent notification '{}' to {} recipient(s)", subject, recipients.length);
    } catch (RuntimeException ex) {
      // Best-effort: never let a delivery failure break the business operation.
      log.warn("Failed to send notification '{}': {}", subject, ex.getMessage());
    }
  }
}
