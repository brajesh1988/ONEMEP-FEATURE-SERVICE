package com.netlink.onemep_feature.common.dto;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.Data;

@Data
public class GenericListRequestDTO {
  @Valid private PaginationAndSortingDTO paginationAndSorting;
  private Map<String, Object> filters;
}
