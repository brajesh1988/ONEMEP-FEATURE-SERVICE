package com.netlink.onemep_feature.project.repo;

import com.netlink.onemep_feature.project.dto.TechnicalMasterDto;
import com.netlink.onemep_feature.project.model.ProjectTechnicalAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTechnicalAttachmentRepo
    extends JpaRepository<ProjectTechnicalAttachment, Long> {

  /** Metadata-only projection (never selects the {@code file_data} bytes), scoped by project. */
  @Query(
      "SELECT new com.netlink.onemep_feature.project.dto.TechnicalMasterDto$AttachmentMetadata("
          + "a.id, a.fileName, a.contentType, a.fileExtension, a.fileSize, a.createdBy,"
          + " a.createdDate) "
          + "FROM ProjectTechnicalAttachment a WHERE a.technicalMaster.project.id = :projectId "
          + "ORDER BY a.createdDate DESC, a.id DESC")
  List<TechnicalMasterDto.AttachmentMetadata> findMetadataByProjectId(
      @Param("projectId") Long projectId);

  /** Scopes the attachment to the owning project (via its Technical Master). */
  Optional<ProjectTechnicalAttachment> findByIdAndTechnicalMaster_Project_Id(
      Long id, Long projectId);

  long countByTechnicalMaster_Id(Long technicalMasterId);
}
