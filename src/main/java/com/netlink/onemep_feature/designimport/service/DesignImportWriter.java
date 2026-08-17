package com.netlink.onemep_feature.designimport.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.model.DesignSource;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.repo.LookupValueRepo;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts one validated row as a Design (ONEMEP-35).
 *
 * <p><b>One transaction per row</b>, and that is what makes partial success real. A transaction per
 * file would mean a single bad row at the end discarding every good row before it, which is exactly
 * the behaviour the ticket rejects. {@code REQUIRES_NEW} guarantees it even if a caller ever wraps
 * this in a transaction of its own.
 *
 * <p>The cost is a transaction per row, which is slow for a large file. That is the right trade for
 * a background job whose entire purpose is to salvage the rows that are fine.
 *
 * <p>Associations are resolved to managed references here rather than reusing the instances the
 * validator loaded. Those were read in an earlier, already-closed persistence context; {@code
 * getReferenceById} costs no query and removes any question about detached state.
 */
@Component
@RequiredArgsConstructor
public class DesignImportWriter {

  private final DesignRepo designRepo;
  private final ProjectRepo projectRepo;
  private final LookupValueRepo lookupValueRepo;
  private final DesignActivityService designActivityService;

  /**
   * @return the id of the created Design
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long write(Long projectId, ValidatedRow row, String sourceFilename) {
    Design design = new Design();
    design.setProject(projectRepo.getReferenceById(projectId));
    design.setZoneCode(row.zoneCode());
    design.setDiscipline(reference(row.discipline()));
    design.setType(reference(row.type()));
    design.setSubject(reference(row.subject()));
    design.setFloor(reference(row.floor()));
    design.setStage(reference(row.stage()));
    design.setDesignNumber(row.designNumber());
    design.setTitle(row.title());
    design.setTitleNormalized(row.titleNormalized());
    design.setSheetSize(row.sheetSize());
    design.setScale(row.scale());
    design.setPreparedBy(row.preparedBy());
    design.setWorkProgress(row.workProgress());

    // The whole reason DesignSource exists (ONEMEP-38): an imported Design stays distinguishable
    // from one typed into Add Design, for the lifetime of the record.
    design.setSource(DesignSource.IMPORT);
    design.setCreatedBy(SecurityUtils.getUserId().orElse(null));

    Design saved = designRepo.save(design);

    // ONEMEP-43: every Design's trail opens with how it came to exist. Written inside this
    // transaction, so a row that rolls back leaves no trace of having been imported.
    designActivityService.record(
        saved,
        ActivityAction.DESIGN_IMPORTED,
        "Imported from '" + sourceFilename + "' (row " + row.rowNumber() + ")");

    return saved.getId();
  }

  private LookupValue reference(LookupValue detached) {
    return lookupValueRepo.getReferenceById(detached.getId());
  }
}
