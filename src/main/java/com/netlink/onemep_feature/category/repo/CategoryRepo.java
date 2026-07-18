package com.netlink.onemep_feature.category.repo;

import com.netlink.onemep_feature.category.model.CategoryMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepo
    extends JpaRepository<CategoryMaster, Long>, JpaSpecificationExecutor<CategoryMaster> {

  @Query("SELECT c FROM CategoryMaster c WHERE c.active = true ORDER BY c.name ASC")
  List<CategoryMaster> findAllActive();

  @Query("SELECT c FROM CategoryMaster c WHERE LOWER(c.name) = LOWER(:name)")
  Optional<CategoryMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query("SELECT c FROM CategoryMaster c WHERE LOWER(c.name) = LOWER(:name) AND c.id <> :excludeId")
  Optional<CategoryMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);

  @Query("SELECT c FROM CategoryMaster c WHERE LOWER(c.prefix) = LOWER(:prefix)")
  Optional<CategoryMaster> findByPrefixIgnoreCase(@Param("prefix") String prefix);

  Optional<CategoryMaster> findBySeriesCode(Integer seriesCode);
}
