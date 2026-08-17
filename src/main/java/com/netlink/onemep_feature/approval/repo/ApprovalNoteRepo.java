package com.netlink.onemep_feature.approval.repo;

import com.netlink.onemep_feature.approval.model.ApprovalNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalNoteRepo extends JpaRepository<ApprovalNote, Long> {

  @Query("SELECT n FROM ApprovalNote n WHERE n.request.id = :requestId ORDER BY n.id DESC")
  List<ApprovalNote> findForRequest(@Param("requestId") Long requestId);

  @Query(
      """
      SELECT n FROM ApprovalNote n
      WHERE n.request.file.id = :fileId
      ORDER BY n.id DESC
      """)
  List<ApprovalNote> findForFile(@Param("fileId") Long fileId);
}
