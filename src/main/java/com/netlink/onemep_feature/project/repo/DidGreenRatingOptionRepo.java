package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.model.DidGreenRatingOption;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DidGreenRatingOptionRepo extends JpaRepository<DidGreenRatingOption, Long> {

  List<DidGreenRatingOption> findByActiveTrueOrderByOptionOrderAsc();

  Optional<DidGreenRatingOption> findByCodeAndActiveTrue(String code);
}
