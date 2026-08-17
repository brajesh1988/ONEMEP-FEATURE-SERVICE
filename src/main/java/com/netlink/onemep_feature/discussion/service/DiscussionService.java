package com.netlink.onemep_feature.discussion.service;

import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.discussion.dto.DiscussionDto;

/** Design discussion thread and the notifications its mentions raise (ONEMEP-41). */
public interface DiscussionService {

  ApiResponse<?> listMessages(Long designId, GenericListRequestDTO request);

  ApiResponse<?> post(Long designId, DiscussionDto.PostRequest request);

  /** The signed-in user's notifications, newest first. */
  ApiResponse<?> listNotifications(GenericListRequestDTO request);

  ApiResponse<?> unreadCount();

  ApiResponse<?> markRead(Long notificationId);

  ApiResponse<?> markAllRead();
}
