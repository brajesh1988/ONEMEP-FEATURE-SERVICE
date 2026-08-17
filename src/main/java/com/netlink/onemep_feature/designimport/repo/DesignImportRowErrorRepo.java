package com.netlink.onemep_feature.designimport.repo;

import com.netlink.onemep_feature.designimport.model.DesignImportRowError;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignImportRowErrorRepo extends JpaRepository<DesignImportRowError, Long> {

  /**
   * Row order, then insertion order — so a row failing both duplicate rules reports them in the
   * order they were checked rather than an arbitrary one.
   */
  @Query(
      """
      SELECT e FROM DesignImportRowError e
      WHERE e.file.id = :fileId
      ORDER BY e.rowNumber ASC, e.id ASC
      """)
  List<DesignImportRowError> findForFile(@Param("fileId") Long fileId);
}
