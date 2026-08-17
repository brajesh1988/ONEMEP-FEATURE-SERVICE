package com.netlink.onemep_feature.design.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.dto.DesignDto;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.model.WorkProgress;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.design.validation.DesignTitleRules;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.file.repo.DesignFileRepo;
import com.netlink.onemep_feature.lookup.model.LookupType;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.service.LookupService;
import com.netlink.onemep_feature.project.model.ProjectMaster;
import com.netlink.onemep_feature.project.repo.ProjectRepo;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignServiceImpl implements DesignService {

  private static final Set<String> SORTABLE =
      Set.of("designNumber", "title", "status", "workProgress", "createdDate", "updatedDate");

  private final DesignRepo designRepo;
  private final ProjectRepo projectRepo;
  private final LookupService lookupService;
  private final DesignUniquenessGuard uniquenessGuard;
  private final DesignActivityService designActivityService;
  private final DesignTaskService designTaskService;
  private final DesignFileRepo designFileRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(Long projectId, GenericListRequestDTO request) {
    requireProject(projectId);
    String search = PageableFactory.search(request);
    Page<Design> page =
        designRepo.findAll(listSpec(projectId, search, filters(request)), pageable(request));
    List<DesignDto.ListItem> content = page.getContent().stream().map(this::toListItem).toList();
    return apiResponseAdaptor.success(
        "Designs fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> create(Long projectId, DesignDto.CreateRequest request) {
    ProjectMaster project = requireProject(projectId);

    String zone = DesignNumberGenerator.normalizeZone(request.zoneCode());
    Segments segments = resolveSegments(request);
    String title = DesignTitleRules.requireValid(request.title());
    String titleNormalized = DesignTitleRules.normalize(title);

    String designNumber =
        DesignNumberGenerator.generate(
            project.getProjectNumber(),
            zone,
            segments.discipline().getCode(),
            segments.type().getCode(),
            segments.subject().getCode(),
            segments.floor().getCode(),
            segments.stage().getCode());

    // Two independent checks, never a composite one: a duplicate number is rejected whatever the
    // Title says, and a duplicate Title is rejected whatever the number says.
    uniquenessGuard.requireUniqueDesignNumber(projectId, designNumber, null);
    uniquenessGuard.requireUniqueTitle(projectId, titleNormalized, null);

    Design design = new Design();
    design.setProject(project);
    design.setZoneCode(zone);
    design.setDiscipline(segments.discipline());
    design.setType(segments.type());
    design.setSubject(segments.subject());
    design.setFloor(segments.floor());
    design.setStage(segments.stage());
    design.setDesignNumber(designNumber);
    design.setTitle(title);
    design.setTitleNormalized(titleNormalized);
    design.setSheetSize(trimToNull(request.sheetSize()));
    design.setScale(trimToNull(request.scale()));
    design.setPreparedBy(trimToNull(request.preparedBy()));
    design.setWorkProgress(
        request.workProgress() == null ? WorkProgress.NOT_STARTED : request.workProgress());
    design.setCreatedBy(SecurityUtils.getUserId().orElse(null));

    Design saved = designRepo.save(design);
    // ONEMEP-43: creation is the first entry in every Design's trail.
    designActivityService.record(saved, ActivityAction.DESIGN_CREATED, "Entry created");
    log.info("Created designId={} number={}", saved.getId(), saved.getDesignNumber());

    return apiResponseAdaptor.success("Design created successfully.", toResponse(saved));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Design fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, DesignDto.UpdateRequest request) {
    Design design = require(id);

    String title = DesignTitleRules.requireValid(request.title());
    String titleNormalized = DesignTitleRules.normalize(title);

    // Only the Title is editable, and the Design Number is immutable — so the number rule cannot be
    // broken by an edit and only the Title needs re-checking, excluding this record (ONEMEP-37).
    uniquenessGuard.requireUniqueTitle(design.getProject().getId(), titleNormalized, id);

    // Captured before the change so each event can name both sides, as ONEMEP-43's examples do
    // ("Title changed from 'A' to 'B'"). Only fields that actually moved produce an event — a save
    // with no changes must not manufacture audit noise.
    List<String> changes = new ArrayList<>();
    addChange(changes, "Title", design.getTitle(), title);
    addChange(changes, "Sheet Size", design.getSheetSize(), trimToNull(request.sheetSize()));
    addChange(changes, "Scale", design.getScale(), trimToNull(request.scale()));
    addChange(changes, "Prepared By", design.getPreparedBy(), trimToNull(request.preparedBy()));
    if (request.workProgress() != null) {
      addChange(
          changes, "Work Progress", design.getWorkProgress().name(), request.workProgress().name());
    }

    design.setTitle(title);
    design.setTitleNormalized(titleNormalized);
    design.setSheetSize(trimToNull(request.sheetSize()));
    design.setScale(trimToNull(request.scale()));
    design.setPreparedBy(trimToNull(request.preparedBy()));
    if (request.workProgress() != null) {
      design.setWorkProgress(request.workProgress());
    }
    design.setUpdatedBy(SecurityUtils.getUserId().orElse(null));

    designRepo.save(design);
    changes.forEach(
        change -> designActivityService.record(design, ActivityAction.DESIGN_UPDATED, change));
    log.info("Updated designId={} changes={}", id, changes.size());

    return apiResponseAdaptor.success("Design updated successfully.", toResponse(design));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long id) {
    Design design = require(id);
    designRepo.delete(design);
    log.info("Deleted designId={}", id);
    return apiResponseAdaptor.success("Design deleted successfully.");
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> previewNumber(Long projectId, DesignDto.CreateRequest request) {
    ProjectMaster project = requireProject(projectId);
    String zone = DesignNumberGenerator.normalizeZone(request.zoneCode());
    Segments segments = resolveSegments(request);

    return apiResponseAdaptor.success(
        "Design Number generated successfully.",
        new DesignDto.NumberPreview(
            DesignNumberGenerator.generate(
                project.getProjectNumber(),
                zone,
                segments.discipline().getCode(),
                segments.type().getCode(),
                segments.subject().getCode(),
                segments.floor().getCode(),
                segments.stage().getCode())));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** The five catalogue-backed segments, each resolved with its own type guard. */
  private record Segments(
      LookupValue discipline,
      LookupValue type,
      LookupValue subject,
      LookupValue floor,
      LookupValue stage) {}

  private Segments resolveSegments(DesignDto.CreateRequest request) {
    return new Segments(
        lookupService.requireActive(LookupType.DISCIPLINE, request.disciplineId()),
        lookupService.requireActive(LookupType.DESIGN_TYPE, request.typeId()),
        lookupService.requireActive(LookupType.SUBJECT, request.subjectId()),
        lookupService.requireActive(LookupType.FLOOR, request.floorId()),
        lookupService.requireActive(LookupType.STAGE, request.stageId()));
  }

  private ProjectMaster requireProject(Long projectId) {
    return projectRepo
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
  }

  private Design require(Long id) {
    return designRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("This Design is no longer available."));
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isEmpty() ? null : value;
  }

  /**
   * Adds "&lt;Field&gt; changed from 'a' to 'b'" when — and only when — the value actually moved.
   */
  private static void addChange(List<String> changes, String field, String before, String after) {
    if (java.util.Objects.equals(before, after)) {
      return;
    }
    changes.add(field + " changed from " + quoted(before) + " to " + quoted(after));
  }

  private static String quoted(String value) {
    return value == null || value.isBlank() ? "(empty)" : "'" + value + "'";
  }

  // ── querying ──────────────────────────────────────────────────────────────

  private static Map<String, Object> filters(GenericListRequestDTO request) {
    return request == null || request.getFilters() == null ? Map.of() : request.getFilters();
  }

  private static Long longFilter(Map<String, Object> filters, String key) {
    Object value = filters.get(key);
    if (value == null || value.toString().isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** ONEMEP-35 shows the most recently touched Designs first unless the user sorts otherwise. */
  private PageRequest pageable(GenericListRequestDTO request) {
    PageRequest base = PageableFactory.of(request, SORTABLE);
    String requestedSort =
        request == null || request.getPaginationAndSorting() == null
            ? null
            : request.getPaginationAndSorting().getSortBy();
    return requestedSort != null && SORTABLE.contains(requestedSort)
        ? base
        : PageRequest.of(
            base.getPageNumber(), base.getPageSize(), Sort.by(Sort.Direction.DESC, "updatedDate"));
  }

  /**
   * Project scope, then each selected filter, then the search — all ANDed, as ONEMEP-35 specifies.
   * Search itself spans Design Number, Title and the Discipline code or name.
   */
  private Specification<Design> listSpec(
      Long projectId, String search, Map<String, Object> filters) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("project").get("id"), projectId));

      Long disciplineId = longFilter(filters, "disciplineId");
      if (disciplineId != null) {
        predicates.add(cb.equal(root.get("discipline").get("id"), disciplineId));
      }
      Long stageId = longFilter(filters, "stageId");
      if (stageId != null) {
        predicates.add(cb.equal(root.get("stage").get("id"), stageId));
      }
      Object progress = filters.get("workProgress");
      if (progress != null && !progress.toString().isBlank()) {
        predicates.add(
            cb.equal(
                root.get("workProgress"),
                WorkProgress.valueOf(progress.toString().trim().toUpperCase())));
      }

      if (search != null) {
        String term = "%" + search.toLowerCase() + "%";
        var discipline = root.join("discipline", jakarta.persistence.criteria.JoinType.LEFT);
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("designNumber")), term),
                cb.like(cb.lower(root.get("title")), term),
                cb.like(cb.lower(discipline.get("code")), term),
                cb.like(cb.lower(discipline.get("label")), term)));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  // ── mapping ───────────────────────────────────────────────────────────────

  private static DesignDto.SegmentView segmentView(LookupValue value) {
    return value == null
        ? null
        : new DesignDto.SegmentView(value.getId(), value.getCode(), value.getLabel());
  }

  private DesignDto.ListItem toListItem(Design d) {
    return new DesignDto.ListItem(
        d.getId(),
        d.getDesignNumber(),
        d.getTitle(),
        d.getDiscipline().getCode(),
        d.getStage().getCode(),
        // ONEMEP-35's Doc column: logical files, not revisions. A Design with none reads 0 rather
        // than blank, which the ticket calls out explicitly.
        designFileRepo.countForDesign(d.getId()),
        d.getStatus(),
        d.getWorkProgress(),
        d.getUpdatedDate());
  }

  private DesignDto.Response toResponse(Design d) {
    return new DesignDto.Response(
        d.getId(),
        d.getProject().getId(),
        d.getProject().getProjectNumber(),
        d.getDesignNumber(),
        d.getZoneCode(),
        segmentView(d.getDiscipline()),
        segmentView(d.getType()),
        segmentView(d.getSubject()),
        segmentView(d.getFloor()),
        segmentView(d.getStage()),
        d.getTitle(),
        d.getSheetSize(),
        d.getScale(),
        d.getPreparedBy(),
        d.getWorkProgress(),
        d.getStatus(),
        designTaskService.toView(d),
        d.getVersion(),
        d.getUpdatedBy(),
        d.getUpdatedDate());
  }
}
