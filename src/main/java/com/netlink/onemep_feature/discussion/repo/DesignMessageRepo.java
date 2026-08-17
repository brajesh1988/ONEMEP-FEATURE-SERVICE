package com.netlink.onemep_feature.discussion.repo;

import com.netlink.onemep_feature.discussion.model.DesignMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignMessageRepo extends JpaRepository<DesignMessage, Long> {

  /**
   * Newest first so the most recent conversation is on page 1; the UI reverses within the page to
   * read chronologically. Paging from the oldest end would put a long thread's newest posts on the
   * last page, which ONEMEP-41's "load older progressively" note argues against.
   */
  @Query("SELECT m FROM DesignMessage m WHERE m.design.id = :designId ORDER BY m.id DESC")
  Page<DesignMessage> findForDesign(@Param("designId") Long designId, Pageable pageable);
}
