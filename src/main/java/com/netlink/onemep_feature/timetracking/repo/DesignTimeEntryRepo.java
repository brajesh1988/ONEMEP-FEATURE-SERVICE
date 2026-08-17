package com.netlink.onemep_feature.timetracking.repo;

import com.netlink.onemep_feature.timetracking.model.DesignTimeEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignTimeEntryRepo extends JpaRepository<DesignTimeEntry, Long> {

  /** Latest work date first; within a date, oldest entry first so the day reads in order. */
  @Query(
      """
      SELECT e FROM DesignTimeEntry e
      WHERE e.design.id = :designId
      ORDER BY e.workDate DESC, e.userId ASC, e.id ASC
      """)
  List<DesignTimeEntry> findForDesign(@Param("designId") Long designId);

  /**
   * Committed total for one person on one day, read immediately before accepting a new entry so the
   * 24-hour cap is checked against reality rather than a stale page.
   */
  @Query(
      """
      SELECT COALESCE(SUM(e.hours), 0) FROM DesignTimeEntry e
      WHERE e.design.id = :designId AND e.userId = :userId AND e.workDate = :workDate
      """)
  BigDecimal totalForUserOnDate(
      @Param("designId") Long designId,
      @Param("userId") Long userId,
      @Param("workDate") LocalDate workDate);

  @Query("SELECT COALESCE(SUM(e.hours), 0) FROM DesignTimeEntry e WHERE e.design.id = :designId")
  BigDecimal totalForDesign(@Param("designId") Long designId);

  @Query("SELECT COUNT(DISTINCT e.userId) FROM DesignTimeEntry e WHERE e.design.id = :designId")
  long distinctContributors(@Param("designId") Long designId);

  @Query("SELECT e FROM DesignTimeEntry e WHERE e.id = :id AND e.design.id = :designId")
  Optional<DesignTimeEntry> findByIdAndDesign(
      @Param("id") Long id, @Param("designId") Long designId);
}
