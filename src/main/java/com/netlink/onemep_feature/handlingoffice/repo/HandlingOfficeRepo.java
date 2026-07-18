package com.netlink.onemep_feature.handlingoffice.repo;

import com.netlink.onemep_feature.handlingoffice.model.HandlingOfficeMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HandlingOfficeRepo
    extends JpaRepository<HandlingOfficeMaster, Long>,
        JpaSpecificationExecutor<HandlingOfficeMaster> {

  @Query("SELECT h FROM HandlingOfficeMaster h WHERE h.active = true ORDER BY h.name ASC")
  List<HandlingOfficeMaster> findAllActive();

  @Query("SELECT h FROM HandlingOfficeMaster h WHERE LOWER(h.name) = LOWER(:name)")
  Optional<HandlingOfficeMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query(
      "SELECT h FROM HandlingOfficeMaster h WHERE LOWER(h.name) = LOWER(:name) AND h.id <>"
          + " :excludeId")
  Optional<HandlingOfficeMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);
}
