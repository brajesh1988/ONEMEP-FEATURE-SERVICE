package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.DidPartyType;
import com.netlink.onemep_feature.project.model.ProjectDidContact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDidContactRepo extends JpaRepository<ProjectDidContact, Long> {

  List<ProjectDidContact> findByTechnicalMaster_IdAndPartyTypeOrderByContactOrderAscIdAsc(
      Long technicalMasterId, DidPartyType partyType);
}
