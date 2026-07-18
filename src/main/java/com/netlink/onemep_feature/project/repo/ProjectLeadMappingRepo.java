package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectLeadMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectLeadMappingRepo extends JpaRepository<ProjectLeadMapping, Long> {

  List<ProjectLeadMapping> findByProject_Id(Long projectId);

  void deleteByProject_Id(Long projectId);
}
