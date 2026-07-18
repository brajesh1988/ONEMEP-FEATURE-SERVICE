package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectDeliverySchedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDeliveryScheduleRepo extends JpaRepository<ProjectDeliverySchedule, Long> {

  List<ProjectDeliverySchedule> findByProject_IdOrderByPlannedDateAscIdAsc(Long projectId);

  Optional<ProjectDeliverySchedule> findByIdAndProject_Id(Long id, Long projectId);
}
