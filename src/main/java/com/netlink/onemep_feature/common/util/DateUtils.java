package com.netlink.onemep_feature.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class DateUtils {
  private DateUtils() {}

  public static LocalDateTime getCurrentUtcTime() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}
