package com.netlink.onemep_feature.design.repo;

import com.netlink.onemep_feature.design.model.DesignTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignTagRepo extends JpaRepository<DesignTag, Long> {

  @Query("SELECT t FROM DesignTag t WHERE t.design.id = :designId ORDER BY t.label ASC")
  List<DesignTag> findForDesign(@Param("designId") Long designId);

  @Query(
      """
      SELECT t FROM DesignTag t
      WHERE t.design.id = :designId AND t.labelNormalized = :labelNormalized
      """)
  Optional<DesignTag> findByDesignAndNormalizedLabel(
      @Param("designId") Long designId, @Param("labelNormalized") String labelNormalized);

  @Query("SELECT t FROM DesignTag t WHERE t.id = :id AND t.design.id = :designId")
  Optional<DesignTag> findByIdAndDesign(@Param("id") Long id, @Param("designId") Long designId);
}
