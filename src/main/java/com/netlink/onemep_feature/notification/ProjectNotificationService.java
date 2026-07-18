package com.netlink.onemep_feature.notification;

import com.netlink.onemep_feature.project.model.ProjectMaster;
import java.util.List;

/**
 * Sends stakeholder notifications when a project's lifecycle or priority changes (ONEMEP-14).
 *
 * <p>Implementations are best-effort: a delivery failure must never roll back or fail the
 * originating business transaction.
 */
public interface ProjectNotificationService {

  void notifyLifecycleChanged(
      ProjectMaster project, String oldStatus, String newStatus, List<Long> leadUserIds);

  void notifyPriorityChanged(
      ProjectMaster project, String oldPriority, String newPriority, List<Long> leadUserIds);
}
