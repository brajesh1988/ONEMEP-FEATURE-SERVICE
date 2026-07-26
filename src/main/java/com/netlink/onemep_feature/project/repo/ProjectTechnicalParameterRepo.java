package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectTechnicalParameter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTechnicalParameterRepo
    extends JpaRepository<ProjectTechnicalParameter, Long> {

  List<ProjectTechnicalParameter> findByTechnicalMaster_IdOrderByScopeAscIdAsc(
      Long technicalMasterId);

  long countByTechnicalMaster_IdAndScope(Long technicalMasterId, String scope);

  void deleteByTechnicalMaster_Id(Long technicalMasterId);
}
