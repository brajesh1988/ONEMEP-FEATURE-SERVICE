package com.netlink.onemep_feature.common.adaptor;

import com.netlink.onemep_feature.common.dto.ApiError;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.ErrorCode;
import com.netlink.onemep_feature.common.util.DateUtils;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseAdaptor {

  public <T> ApiResponse<T> success(String message, T data) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .timestamp(DateUtils.getCurrentUtcTime())
        .data(data)
        .build();
  }

  public <T> ApiResponse<T> success(String message) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .timestamp(DateUtils.getCurrentUtcTime())
        .build();
  }

  public ApiResponse<Void> error(
      ErrorCode code, String message, List<String> details, boolean display) {
    return ApiResponse.<Void>builder()
        .success(false)
        .timestamp(DateUtils.getCurrentUtcTime())
        .error(
            ApiError.builder()
                .code(code.name())
                .message(message)
                .details(details)
                .display(display)
                .build())
        .build();
  }

  public ApiResponse<Void> error(ErrorCode code, String message, boolean display) {
    return error(code, message, List.of(), display);
  }
}
