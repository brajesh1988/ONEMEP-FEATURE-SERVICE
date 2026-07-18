package com.netlink.onemep_feature.tier.repo;

import com.netlink.onemep_feature.tier.model.TierMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TierRepo
    extends JpaRepository<TierMaster, Long>, JpaSpecificationExecutor<TierMaster> {

  @Query("SELECT t FROM TierMaster t WHERE t.active = true ORDER BY t.name ASC")
  List<TierMaster> findAllActive();

  @Query("SELECT t FROM TierMaster t WHERE LOWER(t.name) = LOWER(:name)")
  Optional<TierMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query("SELECT t FROM TierMaster t WHERE LOWER(t.name) = LOWER(:name) AND t.id <> :excludeId")
  Optional<TierMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);
}
