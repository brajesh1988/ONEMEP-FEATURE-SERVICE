package com.netlink.onemep_feature.approval.service;

import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import com.netlink.onemep_feature.project.repo.ProjectMemberMappingRepo;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Works out who the Principal is for a Project (ONEMEP-40).
 *
 * <p>ONEMEP-40 routes to "the Principal" without ever defining one. Resolved here as a role rather
 * than a person: a team role carries the principal flag, and the Project's Principal is whichever
 * of its members holds such a role. Nothing hardcodes a user, the answer is per-project, and
 * reassigning the role moves the Principal with it.
 *
 * <p>More than one holder is allowed and treated as all of them — consistent with the rest of the
 * flow, where multiple approvers must all approve.
 */
@Component
@RequiredArgsConstructor
public class PrincipalResolver {

  private final ProjectMemberMappingRepo projectMemberMappingRepo;

  /** Principal user ids for the Project, or empty when the role is unassigned there. */
  public List<Long> principalsOf(Long projectId) {
    return projectMemberMappingRepo.findByProject_Id(projectId).stream()
        .filter(m -> m.getTeamRole() != null && Boolean.TRUE.equals(m.getTeamRole().getPrincipal()))
        .map(ProjectMemberMapping::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public boolean isPrincipal(Long projectId, Long userId) {
    return userId != null && principalsOf(projectId).contains(userId);
  }
}
