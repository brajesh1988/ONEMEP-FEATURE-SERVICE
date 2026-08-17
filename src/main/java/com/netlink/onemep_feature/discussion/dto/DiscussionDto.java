package com.netlink.onemep_feature.discussion.dto;

import com.netlink.onemep_feature.discussion.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for the Design discussion thread and its notifications (ONEMEP-41). */
public final class DiscussionDto {
  private DiscussionDto() {}

  public record PostRequest(
      @NotBlank(message = "Enter a message.")
          @Size(max = 4000, message = "Message must not exceed 4000 characters.")
          String body) {}

  public record MentionView(Long userId, String displayName) {}

  public record MessageView(
      Long id,
      String body,
      String author,
      Long authorId,
      LocalDateTime postedAt,
      List<MentionView> mentions) {}

  public record NotificationView(
      Long id,
      NotificationType type,
      String title,
      String body,
      Long designId,
      boolean read,
      LocalDateTime createdDate) {}

  public record UnreadCount(long unread) {}
}
