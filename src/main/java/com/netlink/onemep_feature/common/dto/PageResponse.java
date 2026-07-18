package com.netlink.onemep_feature.common.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
public class PageResponse<T> {
  private List<T> content;
  private long totalElements;
  private int totalPages;
  private int pageNumber;
  private int pageSize;

  public PageResponse(Page<?> page, List<T> content) {
    this.content = content;
    this.pageNumber = page.getNumber();
    this.totalPages = page.getTotalPages();
    this.totalElements = page.getTotalElements();
    this.pageSize = page.getSize();
  }
}
