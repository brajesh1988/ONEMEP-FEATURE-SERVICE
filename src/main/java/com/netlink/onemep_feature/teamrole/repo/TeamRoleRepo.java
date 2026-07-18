package com.netlink.onemep_feature.teamrole.repo;

import com.netlink.onemep_feature.teamrole.model.TeamRoleMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRoleRepo
    extends JpaRepository<TeamRoleMaster, Long>, JpaSpecificationExecutor<TeamRoleMaster> {

  @Query("SELECT tr FROM TeamRoleMaster tr WHERE tr.active = true ORDER BY tr.name ASC")
  List<TeamRoleMaster> findAllActive();

  @Query("SELECT tr FROM TeamRoleMaster tr WHERE LOWER(tr.name) = LOWER(:name)")
  Optional<TeamRoleMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query(
      "SELECT tr FROM TeamRoleMaster tr WHERE LOWER(tr.name) = LOWER(:name) AND tr.id <>"
          + " :excludeId")
  Optional<TeamRoleMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);

  boolean existsByTier_Id(Long tierId);
}
