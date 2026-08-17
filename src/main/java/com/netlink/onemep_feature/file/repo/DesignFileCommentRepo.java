package com.netlink.onemep_feature.file.repo;

import com.netlink.onemep_feature.file.model.DesignFileComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignFileCommentRepo extends JpaRepository<DesignFileComment, Long> {

  @Query(
      """
      SELECT c FROM DesignFileComment c
      WHERE c.version.file.id = :fileId
      ORDER BY c.version.revisionNo DESC, c.id ASC
      """)
  List<DesignFileComment> findForFile(@Param("fileId") Long fileId);

  @Query("SELECT COUNT(c) FROM DesignFileComment c WHERE c.version.id = :versionId")
  long countForVersion(@Param("versionId") Long versionId);

  /** Drives the badge, and gates final approval in ONEMEP-40. */
  @Query(
      """
      SELECT COUNT(c) FROM DesignFileComment c
      WHERE c.version.file.id = :fileId AND c.status = com.netlink.onemep_feature.file.model.CommentStatus.OPEN
      """)
  long countOpenForFile(@Param("fileId") Long fileId);

  /** Comments cascade with their file, but only because this removes them first. */
  @Modifying
  @Query(
      "DELETE FROM DesignFileComment c WHERE c.version.id IN"
          + " (SELECT v.id FROM DesignFileVersion v WHERE v.file.id = :fileId)")
  void deleteAllForFile(@Param("fileId") Long fileId);
}
