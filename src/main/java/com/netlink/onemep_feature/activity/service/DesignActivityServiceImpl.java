package com.netlink.onemep_feature.activity.service;

import com.netlink.onemep_feature.activity.dto.ActivityDto;
import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.model.DesignActivityLog;
import com.netlink.onemep_feature.activity.repo.DesignActivityLogRepo;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PageResponse;
import com.netlink.onemep_feature.common.dto.PaginationAndSortingDTO;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignActivityServiceImpl implements DesignActivityService {

  /** Shown for platform-driven events, where no person is responsible. */
  static final String SYSTEM_ACTOR = "System";

  private static final int MAX_DETAIL_LENGTH = 1000;

  private final DesignActivityLogRepo designActivityLogRepo;
  private final DesignRepo designRepo;
  private final ApiResponseAdaptor apiResponseAdaptor;

  /**
   * Intentionally without {@code @Transactional} — it inherits the caller's transaction so the
   * audit row and the change it describes commit or roll back together. See {@link
   * DesignActivityService}.
   */
  @Override
  public void record(Design design, ActivityAction action, String detail) {
    DesignActivityLog entry = new DesignActivityLog();
    entry.setDesign(design);
    entry.setAction(action);
    entry.setDetail(truncate(detail));
    entry.setActorLabel(currentActorLabel().orElse(null));
    entry.setCreatedBy(SecurityUtils.getUserId().orElse(null));
    designActivityLogRepo.save(entry);
  }

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> list(Long designId, GenericListRequestDTO request) {
    if (!designRepo.existsById(designId)) {
      throw new ResourceNotFoundException("This Design is no longer available.");
    }

    Page<DesignActivityLog> page = designActivityLogRepo.findForDesign(designId, pageable(request));
    List<ActivityDto.Entry> content = page.getContent().stream().map(this::toEntry).toList();

    return apiResponseAdaptor.success(
        page.isEmpty()
            ? "No Activity has been recorded for this Design yet."
            : "Activity fetched successfully.",
        new PageResponse<>(page, content));
  }

  /**
   * Page size and number only — sorting is fixed by the query. An audit trail the caller can
   * re-sort is not an audit trail, and ONEMEP-43 specifies newest-first unconditionally.
   */
  private static PageRequest pageable(GenericListRequestDTO request) {
    PaginationAndSortingDTO ps =
        request != null && request.getPaginationAndSorting() != null
            ? request.getPaginationAndSorting()
            : new PaginationAndSortingDTO();
    return PageRequest.of(ps.getPageNumber(), ps.getPageSize());
  }

  /**
   * Best available display name for the acting user, taken from the JWT so the audit write never
   * depends on the identity service being reachable. Empty when no user is authenticated, which is
   * how platform-driven events are recorded.
   */
  private static Optional<String> currentActorLabel() {
    return SecurityUtils.getJwt()
        .map(
            jwt -> {
              String name = jwt.getClaimAsString("name");
              if (name != null && !name.isBlank()) {
                return name.trim();
              }
              String email = jwt.getClaimAsString("email");
              if (email != null && !email.isBlank()) {
                return email.trim();
              }
              String subject = jwt.getSubject();
              return subject == null || subject.isBlank() ? null : "User " + subject;
            })
        .filter(label -> label != null && !label.isBlank());
  }

  /** Detail is prose, not data; a pathological value is trimmed rather than failing the write. */
  private static String truncate(String detail) {
    String value = detail == null ? "" : detail.trim();
    if (value.isEmpty()) {
      return "(no detail recorded)";
    }
    return value.length() <= MAX_DETAIL_LENGTH
        ? value
        : value.substring(0, MAX_DETAIL_LENGTH - 1) + "…";
  }

  private ActivityDto.Entry toEntry(DesignActivityLog log) {
    return new ActivityDto.Entry(
        log.getId(),
        log.getAction(),
        log.getDetail(),
        log.getCreatedDate(),
        log.getActorLabel() == null ? SYSTEM_ACTOR : log.getActorLabel(),
        log.getCreatedBy());
  }
}
