package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDidStructureConsultantTeam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidStructureConsultantTeamRepo
    extends JpaRepository<ProjectDidStructureConsultantTeam, Long> {

  Optional<ProjectDidStructureConsultantTeam> findByTechnicalMaster_Id(Long technicalMasterId);
}
