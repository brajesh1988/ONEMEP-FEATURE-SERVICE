package com.netlink.onemep_feature.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PaginationAndSortingDTO {
  @Min(value = 0, message = "Page number must be 0 or greater.")
  private int pageNumber = 0;

  @Min(value = 1, message = "Page size must be at least 1.")
  @Max(value = 200, message = "Page size must not exceed 200.")
  private int pageSize = 20;

  private String sortBy = "createdDate";
  private String sortDirection = "DESC";
}
