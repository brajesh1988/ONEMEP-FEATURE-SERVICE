package com.netlink.onemep_feature.discussion.controller;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.discussion.dto.DiscussionDto;
import com.netlink.onemep_feature.discussion.service.DiscussionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Design discussion thread and its mention notifications (ONEMEP-41). */
@RestController
@RequiredArgsConstructor
public class DiscussionController {

  private final DiscussionService discussionService;

  @Operation(
      summary = "List a Design's discussion messages",
      tags = {"Discussion"})
  @PostMapping("/designs/{designId}/messages/list")
  public ResponseEntity<ApiResponse<?>> list(
      @PathVariable @NotNull Long designId, @Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(discussionService.listMessages(designId, request));
  }

  /** Mentions are resolved server-side from the message text; the client sends no id list. */
  @Operation(
      summary = "Post a message to a Design's discussion",
      tags = {"Discussion"})
  @PostMapping("/designs/{designId}/messages")
  public ResponseEntity<ApiResponse<?>> post(
      @PathVariable @NotNull Long designId, @Valid @RequestBody DiscussionDto.PostRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(discussionService.post(designId, request));
  }

  @Operation(
      summary = "List the signed-in user's notifications",
      tags = {"Discussion"})
  @PostMapping("/notifications/list")
  public ResponseEntity<ApiResponse<?>> notifications(
      @Valid @RequestBody GenericListRequestDTO request) {
    return ResponseEntity.ok(discussionService.listNotifications(request));
  }

  @Operation(
      summary = "Fetch the unread notification count",
      tags = {"Discussion"})
  @GetMapping("/notifications/unread-count")
  public ResponseEntity<ApiResponse<?>> unreadCount() {
    return ResponseEntity.ok(discussionService.unreadCount());
  }

  @Operation(
      summary = "Mark one notification as read",
      tags = {"Discussion"})
  @PatchMapping("/notifications/{notificationId}/read")
  public ResponseEntity<ApiResponse<?>> markRead(@PathVariable @NotNull Long notificationId) {
    return ResponseEntity.ok(discussionService.markRead(notificationId));
  }

  @Operation(
      summary = "Mark every notification as read",
      tags = {"Discussion"})
  @PatchMapping("/notifications/read-all")
  public ResponseEntity<ApiResponse<?>> markAllRead() {
    return ResponseEntity.ok(discussionService.markAllRead());
  }
}
