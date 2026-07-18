package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.ProjectMaster;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepo
    extends JpaRepository<ProjectMaster, Long>, JpaSpecificationExecutor<ProjectMaster> {

  @Query("SELECT p FROM ProjectMaster p WHERE LOWER(p.name) = LOWER(:name)")
  Optional<ProjectMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query("SELECT p FROM ProjectMaster p WHERE LOWER(p.name) = LOWER(:name) AND p.id <> :excludeId")
  Optional<ProjectMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);

  boolean existsByCategory_Id(Long categoryId);
}
