package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectMemberMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMemberMappingRepo extends JpaRepository<ProjectMemberMapping, Long> {

  List<ProjectMemberMapping> findByProject_Id(Long projectId);

  void deleteByProject_Id(Long projectId);

  long countByTeamRole_Id(Long teamRoleId);
}
