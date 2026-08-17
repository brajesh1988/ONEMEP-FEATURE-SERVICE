package com.netlink.onemep_feature.design.repo;

import com.netlink.onemep_feature.design.model.Design;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignRepo extends JpaRepository<Design, Long>, JpaSpecificationExecutor<Design> {

  /**
   * Design Number uniqueness. Independent of the Title: a number already in use is a duplicate
   * whatever the new row is called.
   *
   * <p>{@code excludeId} lets Edit ignore the record being edited.
   */
  @Query(
      """
      SELECT d FROM Design d
      WHERE d.project.id = :projectId
        AND d.designNumber = :designNumber
        AND (:excludeId IS NULL OR d.id <> :excludeId)
      """)
  Optional<Design> findByDesignNumber(
      @Param("projectId") Long projectId,
      @Param("designNumber") String designNumber,
      @Param("excludeId") Long excludeId);

  /**
   * Design Title uniqueness, compared on the normalised form so surrounding space and case cannot
   * defeat it. Independent of the Design Number.
   */
  @Query(
      """
      SELECT d FROM Design d
      WHERE d.project.id = :projectId
        AND d.titleNormalized = :titleNormalized
        AND (:excludeId IS NULL OR d.id <> :excludeId)
      """)
  Optional<Design> findByTitle(
      @Param("projectId") Long projectId,
      @Param("titleNormalized") String titleNormalized,
      @Param("excludeId") Long excludeId);

  @Query("SELECT d FROM Design d WHERE d.id = :id AND d.project.id = :projectId")
  Optional<Design> findByIdAndProject(@Param("id") Long id, @Param("projectId") Long projectId);
}
