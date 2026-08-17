package com.netlink.onemep_feature.file.repo;

import com.netlink.onemep_feature.file.model.DesignFile;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignFileRepo extends JpaRepository<DesignFile, Long> {

  /** Newest logical file first, as the Uploaded Files table displays them. */
  @Query("SELECT f FROM DesignFile f WHERE f.design.id = :designId ORDER BY f.id DESC")
  List<DesignFile> findForDesign(@Param("designId") Long designId);

  @Query("SELECT COUNT(f) FROM DesignFile f WHERE f.design.id = :designId")
  long countForDesign(@Param("designId") Long designId);

  @Query(
      """
      SELECT f FROM DesignFile f
      WHERE f.design.id = :designId AND f.displayNameNormalized = :normalized
      """)
  Optional<DesignFile> findByDesignAndNormalizedName(
      @Param("designId") Long designId, @Param("normalized") String normalized);

  /**
   * Takes a write lock on the file row before its revision allocator is read.
   *
   * <p>This is what serialises two concurrent "upload new version" calls on the same file: the
   * second blocks until the first commits, then allocates the following number. Without it both
   * would read the same value and one would lose to {@code uq_design_file_revision}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT f FROM DesignFile f WHERE f.id = :id")
  Optional<DesignFile> findByIdForUpdate(@Param("id") Long id);

  @Query("SELECT COUNT(f) FROM DesignFile f WHERE f.design.id IN :designIds")
  long countForDesigns(@Param("designIds") List<Long> designIds);
}
