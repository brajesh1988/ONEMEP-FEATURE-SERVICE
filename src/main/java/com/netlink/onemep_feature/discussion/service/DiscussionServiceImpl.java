package com.netlink.onemep_feature.discussion.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.dto.PaginationAndSortingDTO;
import com.netlink.onemep_feature.common.util.DateUtils;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.discussion.dto.DiscussionDto;
import com.netlink.onemep_feature.discussion.model.DesignMessage;
import com.netlink.onemep_feature.discussion.model.DesignMessageMention;
import com.netlink.onemep_feature.discussion.model.NotificationType;
import com.netlink.onemep_feature.discussion.model.UserNotification;
import com.netlink.onemep_feature.discussion.repo.DesignMessageRepo;
import com.netlink.onemep_feature.discussion.repo.UserNotificationRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscussionServiceImpl implements DiscussionService {

  private static final int MAX_BODY_LENGTH = 4000;

  private final DesignMessageRepo designMessageRepo;
  private final UserNotificationRepo userNotificationRepo;
  private final DesignRepo designRepo;
  private final ProjectMemberMappingRepo projectMemberMappingRepo;
  private final UserDirectoryClient userDirectoryClient;
  private final DesignActivityService designActivityService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listMessages(Long designId, GenericListRequestDTO request) {
    requireDesign(designId);
    Page<DesignMessage> page = designMessageRepo.findForDesign(designId, pageable(request));

    List<Long> userIds =
        page.getContent().stream()
            .flatMap(
                m ->
                    java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(m.getCreatedBy()),
                        m.getMentions().stream().map(DesignMessageMention::getUserId)))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, UserSummary> users =
        userIds.isEmpty() ? Map.of() : userDirectoryClient.resolve(userIds);

    List<DiscussionDto.MessageView> content =
        page.getContent().stream().map(m -> toView(m, users)).toList();

    return apiResponseAdaptor.success(
        page.isEmpty()
            ? "No messages yet. Start the discussion below."
            : "Messages fetched successfully.",
        new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> post(Long designId, DiscussionDto.PostRequest request) {
    Design design = requireDesign(designId);
    Long authorId = requireCurrentUserId();

    String body = request.body() == null ? "" : request.body().trim();
    if (body.isEmpty()) {
      throw new ApplicationException("Enter a message.");
    }
    if (body.length() > MAX_BODY_LENGTH) {
      throw new ApplicationException("Message must not exceed " + MAX_BODY_LENGTH + " characters.");
    }

    DesignMessage message = new DesignMessage();
    message.setDesign(design);
    message.setBody(body);
    message.setCreatedBy(authorId);

    // Only Project members are candidates, so a name resembling somebody outside the Project can
    // never be notified — ONEMEP-41 forbids that explicitly.
    Map<Long, UserSummary> candidates = mentionCandidates(design);
    Set<Long> mentioned = MentionParser.resolve(body, candidates);
    // Mentioning yourself is allowed in the text but never notifies you.
    mentioned.remove(authorId);
    mentioned.forEach(message::mention);

    DesignMessage saved = designMessageRepo.save(message);
    notifyMentioned(saved, design, mentioned, candidates, authorId);

    designActivityService.record(
        design, ActivityAction.DISCUSSION_POSTED, "Posted a message in Discussion");

    Map<Long, UserSummary> users = candidates;
    return apiResponseAdaptor.success(
        mentioned.isEmpty()
            ? "Message posted."
            : "Message posted and "
                + mentioned.stream()
                    .map(id -> nameOf(users, id))
                    .collect(java.util.stream.Collectors.joining(", "))
                + " notified.",
        toView(saved, users));
  }

  /**
   * Notifications are written in the same transaction as the message here, since both are local
   * rows. ONEMEP-41 requires that a delivery failure never rolls back the message — that guarantee
   * belongs to whatever pushes these out (email, websocket), not to recording them.
   */
  private void notifyMentioned(
      DesignMessage message,
      Design design,
      Set<Long> mentioned,
      Map<Long, UserSummary> users,
      Long authorId) {

    String author = nameOf(users, authorId);
    for (Long userId : mentioned) {
      UserNotification notification = new UserNotification();
      notification.setUserId(userId);
      notification.setType(NotificationType.DISCUSSION_MENTION);
      notification.setTitle(author + " mentioned you in " + design.getTitle());
      notification.setBody(preview(message.getBody()) + " · " + design.getDesignNumber());
      notification.setDesign(design);
      notification.setCreatedBy(authorId);
      userNotificationRepo.save(notification);
    }
  }

  // ── notifications ─────────────────────────────────────────────────────────

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> listNotifications(GenericListRequestDTO request) {
    Long userId = requireCurrentUserId();
    Page<UserNotification> page = userNotificationRepo.findForUser(userId, pageable(request));

    List<DiscussionDto.NotificationView> content =
        page.getContent().stream()
            .map(
                n ->
                    new DiscussionDto.NotificationView(
                        n.getId(),
                        n.getType(),
                        n.getTitle(),
                        n.getBody(),
                        n.getDesign() == null ? null : n.getDesign().getId(),
                        Boolean.TRUE.equals(n.getRead()),
                        n.getCreatedDate()))
            .toList();

    return apiResponseAdaptor.success(
        "Notifications fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> unreadCount() {
    return apiResponseAdaptor.success(
        "Unread count fetched successfully.",
        new DiscussionDto.UnreadCount(userNotificationRepo.countUnread(requireCurrentUserId())));
  }

  @Override
  @Transactional
  public ApiResponse<?> markRead(Long notificationId) {
    Long userId = requireCurrentUserId();
    UserNotification notification =
        userNotificationRepo
            .findById(notificationId)
            .filter(n -> Objects.equals(n.getUserId(), userId))
            .orElseThrow(
                () -> new ResourceNotFoundException("This notification is no longer available."));

    if (!Boolean.TRUE.equals(notification.getRead())) {
      notification.setRead(Boolean.TRUE);
      notification.setReadAt(DateUtils.getCurrentUtcTime());
      notification.setUpdatedBy(userId);
      userNotificationRepo.save(notification);
    }
    return apiResponseAdaptor.success("Notification marked as read.");
  }

  @Override
  @Transactional
  public ApiResponse<?> markAllRead() {
    int updated =
        userNotificationRepo.markAllRead(requireCurrentUserId(), DateUtils.getCurrentUtcTime());
    return apiResponseAdaptor.success(updated + " notification(s) marked as read.");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Map<Long, UserSummary> mentionCandidates(Design design) {
    List<Long> memberIds =
        projectMemberMappingRepo.findByProject_Id(design.getProject().getId()).stream()
            .map(ProjectMemberMapping::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberIds.isEmpty() ? Map.of() : userDirectoryClient.resolve(memberIds);
  }

  private Design requireDesign(Long designId) {
    return designRepo
        .findById(designId)
        .orElseThrow(() -> new ResourceNotFoundException("This Design is no longer available."));
  }

  private static PageRequest pageable(GenericListRequestDTO request) {
    PaginationAndSortingDTO ps =
        request != null && request.getPaginationAndSorting() != null
            ? request.getPaginationAndSorting()
            : new PaginationAndSortingDTO();
    return PageRequest.of(ps.getPageNumber(), ps.getPageSize());
  }

  private static String preview(String body) {
    String single = body.replaceAll("\\s+", " ").trim();
    return single.length() <= 120 ? single : single.substring(0, 119) + "…";
  }

  private static Long requireCurrentUserId() {
    return SecurityUtils.getUserId()
        .orElseThrow(() -> new ApplicationException("An authenticated user is required."));
  }

  private static String nameOf(Map<Long, UserSummary> users, Long userId) {
    if (userId == null) {
      return "System";
    }
    UserSummary summary = users.get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  private static DiscussionDto.MessageView toView(
      DesignMessage message, Map<Long, UserSummary> users) {
    return new DiscussionDto.MessageView(
        message.getId(),
        message.getBody(),
        nameOf(users, message.getCreatedBy()),
        message.getCreatedBy(),
        message.getCreatedDate(),
        message.getMentions().stream()
            .map(m -> new DiscussionDto.MentionView(m.getUserId(), nameOf(users, m.getUserId())))
            .toList());
  }
}
