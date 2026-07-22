package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMemberMappingRepo extends JpaRepository<ProjectMemberMapping, Long> {

  List<ProjectMemberMapping> findByProject_Id(Long projectId);

  /**
   * Batch-loads members for a page of projects, eagerly fetching {@code teamRole} (LAZY by default)
   * so the caller can read the role name without triggering an N+1 lazy load per row.
   */
  @Query("select m from ProjectMemberMapping m join fetch m.teamRole where m.project.id in :ids")
  List<ProjectMemberMapping> findByProject_IdIn(@Param("ids") Collection<Long> ids);

  void deleteByProject_Id(Long projectId);

  long countByTeamRole_Id(Long teamRoleId);
}
