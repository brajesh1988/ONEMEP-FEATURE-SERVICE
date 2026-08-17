package com.netlink.onemep_feature.designimport.repo;

import com.netlink.onemep_feature.designimport.model.DesignImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignImportBatchRepo extends JpaRepository<DesignImportBatch, Long> {}
