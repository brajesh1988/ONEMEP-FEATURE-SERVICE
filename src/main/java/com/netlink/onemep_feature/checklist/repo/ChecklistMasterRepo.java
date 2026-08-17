package com.netlink.onemep_feature.checklist.repo;

import com.netlink.onemep_feature.checklist.model.ChecklistMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChecklistMasterRepo
    extends JpaRepository<ChecklistMaster, Long>, JpaSpecificationExecutor<ChecklistMaster> {

  @Query("SELECT c FROM ChecklistMaster c WHERE LOWER(c.name) = LOWER(:name)")
  Optional<ChecklistMaster> findByNameIgnoreCase(@Param("name") String name);

  @Query(
      "SELECT c FROM ChecklistMaster c WHERE LOWER(c.name) = LOWER(:name) AND c.id <> :excludeId")
  Optional<ChecklistMaster> findByNameIgnoreCaseAndIdNot(
      @Param("name") String name, @Param("excludeId") Long excludeId);

  /**
   * Active checklists applicable to a Design's Discipline/Type/Subject.
   *
   * <p>Encodes the ONEMEP-33 rule directly: OR within a segment (satisfied by any matching row) and
   * AND across segments (one EXISTS per segment). A wildcard row — {@code value IS NULL} — matches
   * unconditionally, which is what "Any" means.
   */
  @Query(
      """
      SELECT DISTINCT c FROM ChecklistMaster c
      WHERE c.active = true
        AND EXISTS (SELECT 1 FROM ChecklistApplicability a
                    WHERE a.checklist = c
                      AND a.segment = com.netlink.onemep_feature.checklist.model.ApplicabilitySegment.DISCIPLINE
                      AND (a.value IS NULL OR a.value.id = :disciplineId))
        AND EXISTS (SELECT 1 FROM ChecklistApplicability a
                    WHERE a.checklist = c
                      AND a.segment = com.netlink.onemep_feature.checklist.model.ApplicabilitySegment.DESIGN_TYPE
                      AND (a.value IS NULL OR a.value.id = :typeId))
        AND EXISTS (SELECT 1 FROM ChecklistApplicability a
                    WHERE a.checklist = c
                      AND a.segment = com.netlink.onemep_feature.checklist.model.ApplicabilitySegment.SUBJECT
                      AND (a.value IS NULL OR a.value.id = :subjectId))
      ORDER BY c.id ASC
      """)
  List<ChecklistMaster> findApplicable(
      @Param("disciplineId") Long disciplineId,
      @Param("typeId") Long typeId,
      @Param("subjectId") Long subjectId);
}
