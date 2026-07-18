package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectStakeholder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectStakeholderRepo extends JpaRepository<ProjectStakeholder, Long> {

  List<ProjectStakeholder> findByProject_IdOrderByRoleAscNameAsc(Long projectId);

  Optional<ProjectStakeholder> findByIdAndProject_Id(Long id, Long projectId);
}
