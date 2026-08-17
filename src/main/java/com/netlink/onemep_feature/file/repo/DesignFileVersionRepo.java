package com.netlink.onemep_feature.file.repo;

import com.netlink.onemep_feature.file.model.DesignFileVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignFileVersionRepo extends JpaRepository<DesignFileVersion, Long> {

  /** Version history, newest revision first. */
  @Query("SELECT v FROM DesignFileVersion v WHERE v.file.id = :fileId ORDER BY v.revisionNo DESC")
  List<DesignFileVersion> findForFile(@Param("fileId") Long fileId);

  @Query("SELECT COUNT(v) FROM DesignFileVersion v WHERE v.file.id = :fileId")
  long countForFile(@Param("fileId") Long fileId);

  @Query("SELECT v FROM DesignFileVersion v WHERE v.id = :id AND v.file.id = :fileId")
  Optional<DesignFileVersion> findByIdAndFile(@Param("id") Long id, @Param("fileId") Long fileId);

  /**
   * Storage keys only. Deleting a logical file must not pull its version entities into the
   * persistence context — managed children pointing at a removed parent fail on flush.
   */
  @Query("SELECT v.storageKey FROM DesignFileVersion v WHERE v.file.id = :fileId")
  List<String> findStorageKeysForFile(@Param("fileId") Long fileId);

  @Modifying
  @Query("DELETE FROM DesignFileVersion v WHERE v.file.id = :fileId")
  void deleteAllForFile(@Param("fileId") Long fileId);
}
