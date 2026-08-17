package com.netlink.onemep_feature.activity.repo;

import com.netlink.onemep_feature.activity.model.DesignActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignActivityLogRepo extends JpaRepository<DesignActivityLog, Long> {

  /**
   * Newest first, with the id breaking ties so two events sharing a timestamp keep a stable order
   * across pages. Sorting is fixed rather than caller-supplied — an audit trail read out of
   * chronological order is not an audit trail.
   */
  @Query(
      """
      SELECT a FROM DesignActivityLog a
      WHERE a.design.id = :designId
      ORDER BY a.createdDate DESC, a.id DESC
      """)
  Page<DesignActivityLog> findForDesign(@Param("designId") Long designId, Pageable pageable);
}
