package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDidSpecification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidSpecificationRepo extends JpaRepository<ProjectDidSpecification, Long> {

  List<ProjectDidSpecification> findByTechnicalMaster_IdOrderByIdAsc(Long technicalMasterId);

  long countByTechnicalMaster_Id(Long technicalMasterId);

  void deleteByTechnicalMaster_Id(Long technicalMasterId);
}
