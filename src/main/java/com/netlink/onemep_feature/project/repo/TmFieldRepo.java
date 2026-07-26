package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.TmField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TmFieldRepo extends JpaRepository<TmField, Long> {

  List<TmField> findBySection_IdOrderByFieldOrderAsc(Long sectionId);

  List<TmField> findBySeriesCode(Integer seriesCode);

  boolean existsBySeriesCodeAndFieldKey(Integer seriesCode, String fieldKey);

  long countBySection_Id(Long sectionId);

  void deleteBySection_Id(Long sectionId);
}
