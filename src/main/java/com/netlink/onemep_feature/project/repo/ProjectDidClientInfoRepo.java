package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDidClientInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidClientInfoRepo extends JpaRepository<ProjectDidClientInfo, Long> {

  Optional<ProjectDidClientInfo> findByTechnicalMaster_Id(Long technicalMasterId);
}
