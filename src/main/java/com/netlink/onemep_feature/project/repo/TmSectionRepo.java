package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.TmSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TmSectionRepo extends JpaRepository<TmSection, Long> {

  List<TmSection> findBySeriesCodeOrderBySectionOrderAsc(Integer seriesCode);

  long countBySeriesCode(Integer seriesCode);
}
