package com.netlink.onemep_feature.activity.dto;

import com.netlink.onemep_feature.activity.model.ActivityAction;
import java.time.LocalDateTime;

/** Read-only payloads for the Design Activity section (ONEMEP-43). */
public final class ActivityDto {
  private ActivityDto() {}

  /**
   * One row of the Activity table.
   *
   * @param action the stable code, exposed for filtering rather than display
   * @param detail the sentence shown in the Action column
   * @param updatedOn when the event was recorded — distinct from any business date inside {@code
   *     detail}, which ONEMEP-43 is emphatic about ("Logged 7 h on 6 Aug" recorded on 8 Aug)
   * @param updatedBy who acted, or {@code System} for platform-driven events
   */
  public record Entry(
      Long id,
      ActivityAction action,
      String detail,
      LocalDateTime updatedOn,
      String updatedBy,
      Long updatedById) {}
}
