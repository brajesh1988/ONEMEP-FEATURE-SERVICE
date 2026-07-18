package com.netlink.onemep_feature.common.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiError {
  private String code;
  private String message;
  private List<String> details;
  private Boolean display;
}
