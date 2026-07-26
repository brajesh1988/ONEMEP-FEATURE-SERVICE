package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.TmField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TmFieldRepo extends JpaRepository<TmField, Long> {

  /** Catalog fields applicable to a category's series code, ordered by section then field. */
  @Query(
      "SELECT f FROM TmField f JOIN TmFieldCategory c ON c.fieldId = f.id "
          + "WHERE c.seriesCode = :series ORDER BY f.sectionOrder, f.fieldOrder")
  List<TmField> findByCategorySeries(@Param("series") int series);
}
