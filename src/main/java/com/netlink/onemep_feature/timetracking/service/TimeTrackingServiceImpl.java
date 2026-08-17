package com.netlink.onemep_feature.timetracking.service;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import com.netlink.onemep_feature.activity.service.DesignActivityService;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.util.DateUtils;
import com.netlink.onemep_feature.common.util.SecurityUtils;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.design.repo.DesignRepo;
import com.netlink.onemep_feature.exception.ApplicationException;
import com.netlink.onemep_feature.exception.ResourceNotFoundException;
import com.netlink.onemep_feature.timetracking.dto.TimeTrackingDto;
import com.netlink.onemep_feature.timetracking.model.DesignTimeEntry;
import com.netlink.onemep_feature.timetracking.repo.DesignTimeEntryRepo;
import com.netlink.onemep_feature.user.client.UserDirectoryClient;
import com.netlink.onemep_feature.user.dto.UserSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimeTrackingServiceImpl implements TimeTrackingService {

  private static final BigDecimal DAILY_LIMIT = new BigDecimal("24");

  private final DesignTimeEntryRepo designTimeEntryRepo;
  private final DesignRepo designRepo;
  private final UserDirectoryClient userDirectoryClient;
  private final DesignActivityService designActivityService;
  private final ApiResponseAdaptor apiResponseAdaptor;

  @Override
  @Transactional(readOnly = true)
  public ApiResponse<?> summary(Long designId) {
    requireDesign(designId);
    Long currentUser = SecurityUtils.getUserId().orElse(null);

    List<DesignTimeEntry> entries = designTimeEntryRepo.findForDesign(designId);
    Map<Long, UserSummary> users = resolveUsers(entries);

    // Grouped by user *and* date. The same person legitimately appears once per day they logged
    // against — ONEMEP-42 states that plainly, so nothing here collapses them.
    Map<String, List<DesignTimeEntry>> grouped = new LinkedHashMap<>();
    entries.forEach(
        e ->
            grouped
                .computeIfAbsent(e.getUserId() + "|" + e.getWorkDate(), k -> new ArrayList<>())
                .add(e));

    List<TimeTrackingDto.DayGroup> groups =
        grouped.values().stream().map(g -> toGroup(g, users, currentUser)).toList();

    return apiResponseAdaptor.success(
        entries.isEmpty()
            ? "No time logged yet — Log time to add your first entry."
            : "Time tracking fetched successfully.",
        new TimeTrackingDto.Summary(
            designTimeEntryRepo.totalForDesign(designId),
            designTimeEntryRepo.distinctContributors(designId),
            groups));
  }

  @Override
  @Transactional
  public ApiResponse<?> log(Long designId, TimeTrackingDto.LogRequest request) {
    Design design = requireDesign(designId);
    Long userId = requireCurrentUserId();

    BigDecimal hours = request.hours();
    if (hours == null) {
      throw new ApplicationException("Enter the hours spent.");
    }
    if (hours.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ApplicationException("Enter hours greater than 0.");
    }
    if (hours.compareTo(DAILY_LIMIT) > 0) {
      throw new ApplicationException("A time entry cannot exceed 24 hours.");
    }

    LocalDate workDate = request.workDate();
    if (workDate == null) {
      throw new ApplicationException("Select the date for which the time was worked.");
    }
    // ASSUMPTION: future work dates are rejected. ONEMEP-42 lists this as an open question; for a
    // timesheet, refusing to log work that has not happened is the safer default. Relax if the
    // business says otherwise.
    if (workDate.isAfter(DateUtils.getCurrentUtcTime().toLocalDate())) {
      throw new ApplicationException("The work date cannot be in the future.");
    }

    // Read the committed total immediately before accepting, so the cap is checked against reality
    // rather than whatever the client last saw.
    BigDecimal existing = designTimeEntryRepo.totalForUserOnDate(designId, userId, workDate);
    if (existing.add(hours).compareTo(DAILY_LIMIT) > 0) {
      throw new ApplicationException(
          "This entry would exceed 24 hours for "
              + workDate
              + ". You already have "
              + strip(existing)
              + " hours logged.");
    }

    DesignTimeEntry entry = new DesignTimeEntry();
    entry.setDesign(design);
    entry.setUserId(userId);
    entry.setWorkDate(workDate);
    entry.setHours(hours);
    entry.setNote(trimToNull(request.note()));
    entry.setLoggedAt(DateUtils.getCurrentUtcTime());
    entry.setCreatedBy(userId);
    designTimeEntryRepo.save(entry);

    designActivityService.record(
        design, ActivityAction.TIME_LOGGED, "Logged " + strip(hours) + " h on " + workDate);

    return apiResponseAdaptor.success("Time logged successfully.", summaryData(designId, userId));
  }

  @Override
  @Transactional
  public ApiResponse<?> delete(Long designId, Long entryId) {
    Design design = requireDesign(designId);
    Long userId = requireCurrentUserId();

    DesignTimeEntry entry =
        designTimeEntryRepo
            .findByIdAndDesign(entryId, designId)
            .orElseThrow(
                () -> new ResourceNotFoundException("This time entry is no longer available."));

    // ONEMEP-42: "A normal user shall not be able to delete another person's time entries", and the
    // backend must enforce it independently of whether the UI shows the control.
    if (!Objects.equals(entry.getUserId(), userId)) {
      throw new ApplicationException("You can only manage your own time entries.");
    }

    BigDecimal hours = entry.getHours();
    LocalDate workDate = entry.getWorkDate();
    designTimeEntryRepo.delete(entry);

    designActivityService.record(
        design,
        ActivityAction.TIME_DELETED,
        "Deleted a " + strip(hours) + " h time entry for " + workDate);

    return apiResponseAdaptor.success("Time entry deleted.", summaryData(designId, userId));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private TimeTrackingDto.Summary summaryData(Long designId, Long currentUser) {
    List<DesignTimeEntry> entries = designTimeEntryRepo.findForDesign(designId);
    Map<Long, UserSummary> users = resolveUsers(entries);

    Map<String, List<DesignTimeEntry>> grouped = new LinkedHashMap<>();
    entries.forEach(
        e ->
            grouped
                .computeIfAbsent(e.getUserId() + "|" + e.getWorkDate(), k -> new ArrayList<>())
                .add(e));

    return new TimeTrackingDto.Summary(
        designTimeEntryRepo.totalForDesign(designId),
        designTimeEntryRepo.distinctContributors(designId),
        grouped.values().stream().map(g -> toGroup(g, users, currentUser)).toList());
  }

  private static TimeTrackingDto.DayGroup toGroup(
      List<DesignTimeEntry> group, Map<Long, UserSummary> users, Long currentUser) {

    DesignTimeEntry first = group.get(0);
    boolean mine = Objects.equals(first.getUserId(), currentUser);
    BigDecimal total =
        group.stream().map(DesignTimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new TimeTrackingDto.DayGroup(
        first.getUserId(),
        nameOf(users, first.getUserId()),
        mine,
        first.getWorkDate(),
        total,
        group.size(),
        group.stream()
            .map(
                e ->
                    new TimeTrackingDto.EntryView(
                        e.getId(), e.getHours(), e.getNote(), e.getLoggedAt(), mine))
            .toList());
  }

  private Design requireDesign(Long designId) {
    return designRepo
        .findById(designId)
        .orElseThrow(() -> new ResourceNotFoundException("This Design is no longer available."));
  }

  private Map<Long, UserSummary> resolveUsers(List<DesignTimeEntry> entries) {
    List<Long> ids =
        entries.stream()
            .map(DesignTimeEntry::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return ids.isEmpty() ? Map.of() : userDirectoryClient.resolve(ids);
  }

  private static String nameOf(Map<Long, UserSummary> users, Long userId) {
    if (userId == null) {
      return "System";
    }
    UserSummary summary = users.get(userId);
    return summary == null ? UserSummary.unknown(userId).displayName() : summary.displayName();
  }

  /** Renders 8.00 as "8" and 3.50 as "3.5", which is how the ticket's examples read. */
  private static String strip(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static String trimToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isEmpty() ? null : value;
  }

  private static Long requireCurrentUserId() {
    return SecurityUtils.getUserId()
        .orElseThrow(() -> new ApplicationException("An authenticated user is required."));
  }
}
