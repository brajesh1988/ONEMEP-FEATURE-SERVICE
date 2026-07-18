package com.netlink.onemep_feature.detailinglevel.repo;

import com.netlink.onemep_feature.detailinglevel.model.DetailingLevelMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DetailingLevelRepo
    extends JpaRepository<DetailingLevelMaster, Long>,
        JpaSpecificationExecutor<DetailingLevelMaster> {

  @Query("SELECT d FROM DetailingLevelMaster d WHERE d.active = true ORDER BY d.name ASC")
  List<DetailingLevelMaster> findAllActive();

  @Query("SELECT d FROM DetailingLevelMaster d WHERE LOWER(d.name) = LOWER(:name)")
  Optional<DetailingLevelMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query(
      "SELECT d FROM DetailingLevelMaster d WHERE LOWER(d.name) = LOWER(:name) AND d.id <>"
          + " :excludeId")
  Optional<DetailingLevelMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);
}
