package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectActivityLogRepo extends JpaRepository<ProjectActivityLog, Long> {

  /** Most-recent-first activity for the project overview. */
  List<ProjectActivityLog> findByProject_IdOrderByCreatedDateDescIdDesc(Long projectId);
}
