package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectTechnicalFieldValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTechnicalFieldValueRepo
    extends JpaRepository<ProjectTechnicalFieldValue, Long> {

  List<ProjectTechnicalFieldValue> findByTechnicalMaster_Id(Long technicalMasterId);

  void deleteByTechnicalMaster_Id(Long technicalMasterId);
}
