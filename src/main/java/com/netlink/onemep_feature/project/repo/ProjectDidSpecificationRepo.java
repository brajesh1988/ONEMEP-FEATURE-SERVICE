package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDidSpecification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidSpecificationRepo extends JpaRepository<ProjectDidSpecification, Long> {

  Optional<ProjectDidSpecification> findByTechnicalMaster_Id(Long technicalMasterId);
}
