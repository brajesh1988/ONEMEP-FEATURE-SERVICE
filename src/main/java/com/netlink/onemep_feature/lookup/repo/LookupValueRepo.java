package com.netlink.onemep_feature.lookup.repo;

import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LookupValueRepo
    extends JpaRepository<LookupValue, Long>, JpaSpecificationExecutor<LookupValue> {

  @Query(
      """
      SELECT l FROM LookupValue l
      WHERE l.lookupType = :type AND l.active = true
      ORDER BY l.sortOrder ASC, l.code ASC
      """)
  List<LookupValue> findActiveByType(@Param("type") LookupType type);

  @Query(
      """
      SELECT l FROM LookupValue l
      WHERE l.lookupType = :type AND UPPER(l.code) = UPPER(:code)
      """)
  Optional<LookupValue> findByTypeAndCode(
      @Param("type") LookupType type, @Param("code") String code);

  @Query(
      """
      SELECT l FROM LookupValue l
      WHERE l.lookupType = :type AND UPPER(l.code) = UPPER(:code) AND l.id <> :excludeId
      """)
  Optional<LookupValue> findByTypeAndCodeExcluding(
      @Param("type") LookupType type,
      @Param("code") String code,
      @Param("excludeId") Long excludeId);

  /**
   * Resolves several ids at once and keeps the type guard in SQL. Used when validating a
   * multi-select payload — a caller cannot smuggle in an id from another catalogue.
   */
  @Query(
      """
      SELECT l FROM LookupValue l
      WHERE l.lookupType = :type AND l.id IN :ids
      """)
  List<LookupValue> findAllByTypeAndIdIn(
      @Param("type") LookupType type, @Param("ids") List<Long> ids);
}
