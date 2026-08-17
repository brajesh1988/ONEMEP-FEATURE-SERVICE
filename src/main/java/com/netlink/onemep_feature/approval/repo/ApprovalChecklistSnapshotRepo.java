package com.netlink.onemep_feature.approval.repo;

import com.netlink.onemep_feature.approval.model.ApprovalChecklistSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalChecklistSnapshotRepo
    extends JpaRepository<ApprovalChecklistSnapshot, Long> {

  @Query(
      """
      SELECT s FROM ApprovalChecklistSnapshot s
      WHERE s.request.id = :requestId ORDER BY s.sortOrder ASC
      """)
  List<ApprovalChecklistSnapshot> findForRequest(@Param("requestId") Long requestId);
}
