package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDidArchitectTeam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidArchitectTeamRepo extends JpaRepository<ProjectDidArchitectTeam, Long> {

  Optional<ProjectDidArchitectTeam> findByTechnicalMaster_Id(Long technicalMasterId);
}
