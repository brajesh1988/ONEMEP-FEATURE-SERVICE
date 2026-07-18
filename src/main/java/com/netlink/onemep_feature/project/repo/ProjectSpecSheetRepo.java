package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.dto.SpecSheetDto;
import com.netlink.onemep_feature.project.model.ProjectSpecSheet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectSpecSheetRepo extends JpaRepository<ProjectSpecSheet, Long> {

  /** Metadata-only projection (never selects the {@code file_data} bytes). */
  @Query(
      "SELECT new com.netlink.onemep_feature.project.dto.SpecSheetDto$Metadata("
          + "s.id, s.fileName, s.contentType, s.fileExtension, s.fileSize, s.createdBy,"
          + " s.createdDate) "
          + "FROM ProjectSpecSheet s WHERE s.project.id = :projectId ORDER BY s.createdDate DESC,"
          + " s.id DESC")
  List<SpecSheetDto.Metadata> findMetadataByProjectId(@Param("projectId") Long projectId);

  Optional<ProjectSpecSheet> findByIdAndProject_Id(Long id, Long projectId);
}
