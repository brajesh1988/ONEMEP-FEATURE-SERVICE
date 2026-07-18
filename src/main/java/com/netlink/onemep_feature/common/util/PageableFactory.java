package com.netlink.onemep_feature.common.util;

import com.netlink.onemep_feature.common.dto.GenericListRequestDTO;
import com.netlink.onemep_feature.common.dto.PaginationAndSortingDTO;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Builds a safe {@link PageRequest} from the generic list request, guarding the sort column. */
public final class PageableFactory {
  private PageableFactory() {}

  public static PageRequest of(GenericListRequestDTO request, Set<String> allowedSortFields) {
    PaginationAndSortingDTO ps =
        request != null && request.getPaginationAndSorting() != null
            ? request.getPaginationAndSorting()
            : new PaginationAndSortingDTO();

    String sortBy = ps.getSortBy();
    if (sortBy == null || sortBy.isBlank() || !allowedSortFields.contains(sortBy)) {
      sortBy = "createdDate";
    }
    Sort.Direction direction =
        "ASC".equalsIgnoreCase(ps.getSortDirection()) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return PageRequest.of(ps.getPageNumber(), ps.getPageSize(), Sort.by(direction, sortBy));
  }

  /** Pulls the free-text search term from the {@code filters} map ("search" or "name"). */
  public static String search(GenericListRequestDTO request) {
    if (request == null || request.getFilters() == null) {
      return null;
    }
    Map<String, Object> filters = request.getFilters();
    Object value = filters.getOrDefault("search", filters.get("name"));
    if (value == null) {
      return null;
    }
    String term = value.toString().trim();
    return term.isEmpty() ? null : term;
  }
}
