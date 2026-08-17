package com.netlink.onemep_feature.designimport.repo;

import com.netlink.onemep_feature.designimport.model.DesignImportFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignImportFileRepo extends JpaRepository<DesignImportFile, Long> {

  /** Upload order, which is the order the status list is shown in. */
  @Query("SELECT f FROM DesignImportFile f WHERE f.batch.id = :batchId ORDER BY f.ordinal ASC")
  List<DesignImportFile> findForBatch(@Param("batchId") Long batchId);
}
