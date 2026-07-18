package com.netlink.onemep_feature.technical.repo;

import com.netlink.onemep_feature.technical.model.TechnicalMaster;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TechnicalMasterRepo
    extends JpaRepository<TechnicalMaster, Long>, JpaSpecificationExecutor<TechnicalMaster> {

  long countByUnit_Id(Long unitId);

  @Query("SELECT t FROM TechnicalMaster t WHERE LOWER(t.name) = LOWER(:name)")
  Optional<TechnicalMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query(
      "SELECT t FROM TechnicalMaster t WHERE LOWER(t.name) = LOWER(:name) AND t.id <> :excludeId")
  Optional<TechnicalMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);
}
