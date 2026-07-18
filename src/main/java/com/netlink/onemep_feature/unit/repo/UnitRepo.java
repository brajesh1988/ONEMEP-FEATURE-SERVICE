package com.netlink.onemep_feature.unit.repo;

import com.netlink.onemep_feature.unit.model.UnitMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepo
    extends JpaRepository<UnitMaster, Long>, JpaSpecificationExecutor<UnitMaster> {

  @Query("SELECT u FROM UnitMaster u WHERE u.active = true ORDER BY u.name ASC")
  List<UnitMaster> findAllActive();

  @Query("SELECT u FROM UnitMaster u WHERE LOWER(u.symbol) = LOWER(:symbol)")
  Optional<UnitMaster> findBySymbolIgnoreCase(@Param("symbol") String symbol);

  @Query("SELECT u FROM UnitMaster u WHERE LOWER(u.symbol) = LOWER(:symbol) AND u.id <> :excludeId")
  Optional<UnitMaster> findBySymbolIgnoreCaseAndIdNot(
      @Param("symbol") String symbol, @Param("excludeId") Long excludeId);
}
