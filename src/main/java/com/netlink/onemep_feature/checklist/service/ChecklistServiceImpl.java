package com.netlink.onemep_feature.checklist.service;

import static com.netlink.onemep_feature.checklist.validation.ChecklistTextRules.MAX_ITEMS;

import com.netlink.onemep_feature.checklist.dto.ChecklistDto;
import com.netlink.onemep_feature.checklist.model.ApplicabilitySegment;
import com.netlink.onemep_feature.checklist.model.ChecklistApplicability;
import com.netlink.onemep_feature.checklist.model.ChecklistItem;
import com.netlink.onemep_feature.checklist.model.ChecklistMaster;
import com.netlink.onemep_feature.checklist.model.ChecklistRecordType;
import com.netlink.onemep_feature.checklist.repo.ChecklistMasterRepo;
import com.netlink.onemep_feature.checklist.validation.ChecklistTextRules;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.util.PageableFactory;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.DuplicateResourceException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.lookup.model.LookupValue;
import com.netlink.onemep_feature.lookup.service.LookupService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
public class ChecklistServiceImpl implements ChecklistService {

  private static final Set<String> SORTABLE =
      Set.of("name", "active", "createdDate", "updatedDate");

  private final ChecklistMasterRepo checklistMasterRepo;
  private final LookupService lookupService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(GenericListRequestDTO request) {
    String search = PageableFactory.search(request);
    Page<ChecklistMaster> page = checklistMasterRepo.findAll(searchSpec(search), pageable(request));
    List<ChecklistDto.ListItem> content = page.getContent().stream().map(this::toListItem).toList();
    return apiResponseAdaptor.success(
        "Checklists fetched successfully.", new PageResponse<>(page, content));
  }

  @Override
  @Transactional
  public ApiResponse<?> create(ChecklistDto.CreateRequest request) {
    ChecklistMaster checklist = new ChecklistMaster();
    checklist.setRecordType(request.recordType());
    checklist.setName(resolveName(request.recordType(), request.name(), null));
    checklist.setActive(request.active() == null ? Boolean.TRUE : request.active());
    checklist.replaceItems(validateItems(request.recordType(), request.items()));
    applyApplicability(checklist, request.appliesTo());
    checklist.setCreatedBy(SecurityUtils.getUserId().orElse(null));

    ChecklistMaster saved = checklistMasterRepo.save(checklist);
    log.info("Created checklistId={} type={}", saved.getId(), saved.getRecordType());

    return apiResponseAdaptor.success(
        saved.getRecordType() == ChecklistRecordType.CHECKLIST
            ? "Checklist created successfully."
            : "Single Item created successfully.",
        toResponse(saved));
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> get(Long id) {
    return apiResponseAdaptor.success("Checklist fetched successfully.", toResponse(require(id)));
  }

  @Override
  @Transactional
  public ApiResponse<?> update(Long id, ChecklistDto.UpdateRequest request) {
    ChecklistMaster checklist = require(id);

    // ONEMEP-34: reject a type change even when it arrives outside the normal UI.
    if (request.recordType() != null && request.recordType() != checklist.getRecordType()) {
      throw new ApplicationException("Record type cannot be changed after creation.");
    }

    checklist.setName(resolveName(checklist.getRecordType(), request.name(), id));
    checklist.replaceItems(validateItems(checklist.getRecordType(), request.items()));
    applyApplicability(checklist, request.appliesTo());
    if (request.active() != null) {
      checklist.setActive(request.active());
    }
    checklist.setUpdatedBy(SecurityUtils.getUserId().orElse(null));

    checklistMasterRepo.save(checklist);
    log.info("Updated checklistId={}", id);

    return apiResponseAdaptor.success(
        checklist.getRecordType() == ChecklistRecordType.CHECKLIST
            ? "Checklist updated successfully."
            : "Single Item updated successfully.",
        toResponse(checklist));
  }

  @Override
  @Transactional
  public ApiResponse<?> updateStatus(Long id, Boolean active) {
    ChecklistMaster checklist = require(id);
    checklist.setActive(active);
    checklist.setUpdatedBy(SecurityUtils.getUserId().orElse(null));
    checklistMasterRepo.save(checklist);
    return apiResponseAdaptor.success(
        Boolean.TRUE.equals(active)
            ? "Record activated successfully."
            : "Record deactivated successfully.");
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> impact(Long id) {
    ChecklistMaster checklist = require(id);
    // ONEMEP-32 requires the number of matching Designs before deletion is confirmed. The design
    // table does not exist until slice 3, so this is genuinely zero today rather than a stub —
    // point it at the design repository when that lands, using the same applicability rule as
    // ChecklistMasterRepo#findApplicable in reverse.
    long matchingDesigns = 0L;
    return apiResponseAdaptor.success(
        "Checklist impact fetched successfully.",
        new ChecklistDto.ImpactView(checklist.getId(), entryName(checklist), matchingDesigns));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long id) {
    ChecklistMaster checklist = require(id);
    checklistMasterRepo.delete(checklist);
    log.info("Deleted checklistId={}", id);
    return apiResponseAdaptor.success("Record deleted successfully.");
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> applicable(Long disciplineId, Long typeId, Long subjectId) {
    List<ChecklistDto.ApplicableItem> items =
        checklistMasterRepo.findApplicable(disciplineId, typeId, subjectId).stream()
            .map(
                c ->
                    new ChecklistDto.ApplicableItem(
                        c.getId(), c.getRecordType(), entryName(c), itemTexts(c)))
            .toList();

    return apiResponseAdaptor.success(
        items.isEmpty()
            ? "No checklist is configured for this Discipline, Type and Subject combination."
            : "Applicable checklists fetched successfully.",
        items);
  }

  // ── validation ────────────────────────────────────────────────────────────

  /**
   * A Checklist must be named and the name unique; a Single Item must not carry one at all —
   * ONEMEP-34 says a name is "not required or accepted" there, so supplying one is an error rather
   * than something to quietly drop.
   */
  private String resolveName(ChecklistRecordType type, String rawName, Long excludeId) {
    if (type == ChecklistRecordType.SINGLE_ITEM) {
      if (rawName != null && !rawName.isBlank()) {
        throw new ApplicationException("Checklist Name does not apply to a Single Item record.");
      }
      return null;
    }

    String name = ChecklistTextRules.requireValidName(rawName);
    var duplicate =
        excludeId == null
            ? checklistMasterRepo.findByNameIgnoreCase(name)
            : checklistMasterRepo.findByNameIgnoreCaseAndIdNot(name, excludeId);
    duplicate.ifPresent(
        existing -> {
          throw new DuplicateResourceException("A Checklist with this name already exists.");
        });
    return name;
  }

  private List<String> validateItems(ChecklistRecordType type, List<String> rawItems) {
    List<String> items = rawItems == null ? List.of() : rawItems;

    if (type == ChecklistRecordType.SINGLE_ITEM) {
      if (items.size() != 1) {
        throw new ApplicationException("A Single Item must contain exactly one item.");
      }
    } else {
      if (items.isEmpty()) {
        throw new ApplicationException("A Checklist must contain at least one item.");
      }
      if (items.size() > MAX_ITEMS) {
        throw new ApplicationException("A Checklist can contain a maximum of 30 items.");
      }
    }

    // Validated independently per row so a blank between two populated items is reported rather
    // than silently dropped (ONEMEP-33).
    return items.stream().map(ChecklistTextRules::requireValidItem).toList();
  }

  /**
   * An empty id list means "Any" for that segment. The wildcard and specific values are therefore
   * mutually exclusive by construction — the payload cannot express both, which is exactly the rule
   * ONEMEP-33 describes as UI behaviour.
   */
  private void applyApplicability(ChecklistMaster checklist, ChecklistDto.AppliesTo appliesTo) {
    applySegment(checklist, ApplicabilitySegment.DISCIPLINE, appliesTo.disciplineIds());
    applySegment(checklist, ApplicabilitySegment.DESIGN_TYPE, appliesTo.typeIds());
    applySegment(checklist, ApplicabilitySegment.SUBJECT, appliesTo.subjectIds());
  }

  /**
   * Reconciles one segment rather than clearing and recreating it.
   *
   * <p>Clearing first would have Hibernate insert the replacement rows before flushing the pending
   * deletes, and re-selecting the same value would then violate {@code
   * uq_checklist_applicability_value}. Keeping untouched rows also avoids rewriting their audit
   * columns on every save.
   */
  private void applySegment(
      ChecklistMaster checklist, ApplicabilitySegment segment, List<Long> ids) {
    List<ChecklistApplicability> rows = checklist.getApplicability();

    if (ids == null || ids.isEmpty()) {
      boolean alreadyWildcard =
          rows.stream().anyMatch(a -> a.getSegment() == segment && a.isWildcard());
      rows.removeIf(a -> a.getSegment() == segment && !a.isWildcard());
      if (!alreadyWildcard) {
        checklist.addApplicability(ChecklistApplicability.any(segment));
      }
      return;
    }

    // requireAllActive applies the type guard, so an id from another catalogue cannot slip in.
    List<LookupValue> desired = lookupService.requireAllActive(segment.lookupType(), ids);
    Set<Long> desiredIds = desired.stream().map(LookupValue::getId).collect(Collectors.toSet());

    rows.removeIf(
        a ->
            a.getSegment() == segment
                && (a.isWildcard() || !desiredIds.contains(a.getValue().getId())));

    Set<Long> retained =
        rows.stream()
            .filter(a -> a.getSegment() == segment && !a.isWildcard())
            .map(a -> a.getValue().getId())
            .collect(Collectors.toSet());

    for (LookupValue value : desired) {
      if (!retained.contains(value.getId())) {
        checklist.addApplicability(ChecklistApplicability.of(segment, value));
      }
    }
  }

  private ChecklistMaster require(Long id) {
    return checklistMasterRepo
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("This record is no longer available."));
  }

  // ── querying ──────────────────────────────────────────────────────────────

  /**
   * ONEMEP-32: "default grid sorting should be based on the Latest added/edited records on top".
   */
  private PageRequest pageable(GenericListRequestDTO request) {
    PageRequest base = PageableFactory.of(request, SORTABLE);
    String requestedSort =
        request == null || request.getPaginationAndSorting() == null
            ? null
            : request.getPaginationAndSorting().getSortBy();
    boolean explicitSort = requestedSort != null && SORTABLE.contains(requestedSort);
    return explicitSort
        ? base
        : PageRequest.of(
            base.getPageNumber(), base.getPageSize(), Sort.by(Sort.Direction.DESC, "updatedDate"));
  }

  /**
   * Matches the values the grid actually displays: the entry name (the Checklist Name, or the item
   * text for a Single Item) and the Applies To codes and labels. Applied to the whole dataset
   * before pagination, as the ticket requires.
   */
  private Specification<ChecklistMaster> searchSpec(String search) {
    return (root, query, cb) -> {
      if (search == null) {
        return cb.conjunction();
      }
      String term = "%" + search.toLowerCase() + "%";
      List<Predicate> matches = new ArrayList<>();

      matches.add(cb.like(cb.lower(cb.coalesce(root.get("name"), "")), term));

      // A Checklist's items are not shown as its name, so only Single Items match on item text.
      Subquery<Long> itemSub = query.subquery(Long.class);
      Root<ChecklistItem> item = itemSub.from(ChecklistItem.class);
      itemSub
          .select(cb.literal(1L))
          .where(cb.equal(item.get("checklist"), root), cb.like(cb.lower(item.get("text")), term));
      matches.add(
          cb.and(
              cb.equal(root.get("recordType"), ChecklistRecordType.SINGLE_ITEM),
              cb.exists(itemSub)));

      Subquery<Long> valueSub = query.subquery(Long.class);
      Root<ChecklistApplicability> applicability = valueSub.from(ChecklistApplicability.class);
      var value = applicability.join("value", JoinType.INNER);
      valueSub
          .select(cb.literal(1L))
          .where(
              cb.equal(applicability.get("checklist"), root),
              cb.or(
                  cb.like(cb.lower(value.get("code")), term),
                  cb.like(cb.lower(value.get("label")), term)));
      matches.add(cb.exists(valueSub));

      // "Any" is a displayed Applies To value, so it is searchable — but only as the whole word,
      // since a prefix match would drag in every wildcard record on a single keystroke.
      if ("any".equals(search.toLowerCase())) {
        Subquery<Long> anySub = query.subquery(Long.class);
        Root<ChecklistApplicability> wildcard = anySub.from(ChecklistApplicability.class);
        anySub
            .select(cb.literal(1L))
            .where(cb.equal(wildcard.get("checklist"), root), cb.isNull(wildcard.get("value")));
        matches.add(cb.exists(anySub));
      }

      return cb.or(matches.toArray(new Predicate[0]));
    };
  }

  // ── mapping ───────────────────────────────────────────────────────────────

  /** ONEMEP-32: a Checklist shows its name; a Single Item shows its item text. */
  private static String entryName(ChecklistMaster c) {
    if (c.getRecordType() == ChecklistRecordType.CHECKLIST) {
      return c.getName();
    }
    return c.getItems().isEmpty() ? "" : c.getItems().get(0).getText();
  }

  private static List<String> itemTexts(ChecklistMaster c) {
    return c.getItems().stream().map(ChecklistItem::getText).toList();
  }

  private ChecklistDto.ListItem toListItem(ChecklistMaster c) {
    return new ChecklistDto.ListItem(
        c.getId(),
        c.getRecordType(),
        entryName(c),
        toAppliesToView(c),
        c.getItems().size(),
        c.getActive(),
        c.getUpdatedDate());
  }

  private ChecklistDto.Response toResponse(ChecklistMaster c) {
    return new ChecklistDto.Response(
        c.getId(),
        c.getRecordType(),
        c.getName(),
        itemTexts(c),
        toAppliesToView(c),
        c.getActive(),
        c.getVersion(),
        c.getUpdatedBy(),
        c.getUpdatedDate());
  }

  private ChecklistDto.AppliesToView toAppliesToView(ChecklistMaster c) {
    return new ChecklistDto.AppliesToView(
        segmentView(c, ApplicabilitySegment.DISCIPLINE),
        segmentView(c, ApplicabilitySegment.DESIGN_TYPE),
        segmentView(c, ApplicabilitySegment.SUBJECT));
  }

  private ChecklistDto.SegmentView segmentView(ChecklistMaster c, ApplicabilitySegment segment) {
    List<ChecklistApplicability> rows =
        c.getApplicability().stream().filter(a -> a.getSegment() == segment).toList();
    boolean any = rows.stream().anyMatch(ChecklistApplicability::isWildcard);
    List<ChecklistDto.ValueView> values =
        any
            ? List.of()
            : rows.stream()
                .map(ChecklistApplicability::getValue)
                .map(v -> new ChecklistDto.ValueView(v.getId(), v.getCode(), v.getLabel()))
                .toList();
    return new ChecklistDto.SegmentView(any, values);
  }
}
