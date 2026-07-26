package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectTechnicalMaster;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTechnicalMasterRepo extends JpaRepository<ProjectTechnicalMaster, Long> {

  Optional<ProjectTechnicalMaster> findByProject_Id(Long projectId);

  boolean existsByProject_Id(Long projectId);
}
