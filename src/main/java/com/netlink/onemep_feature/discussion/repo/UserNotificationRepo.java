package com.netlink.onemep_feature.discussion.repo;

import com.netlink.onemep_feature.discussion.model.UserNotification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationRepo extends JpaRepository<UserNotification, Long> {

  @Query("SELECT n FROM UserNotification n WHERE n.userId = :userId ORDER BY n.id DESC")
  Page<UserNotification> findForUser(@Param("userId") Long userId, Pageable pageable);

  @Query("SELECT COUNT(n) FROM UserNotification n WHERE n.userId = :userId AND n.read = false")
  long countUnread(@Param("userId") Long userId);

  @Modifying
  @Query(
      """
      UPDATE UserNotification n SET n.read = true, n.readAt = :readAt
      WHERE n.userId = :userId AND n.read = false
      """)
  int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
