package com.netlink.onemep_feature.design.dto;

import com.netlink.onemep_feature.design.model.DesignSource;
import com.netlink.onemep_feature.design.model.DesignStatus;
import com.netlink.onemep_feature.design.model.TaskPriority;
import com.netlink.onemep_feature.design.model.TaskReminder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Task section of the Design Detail screen (ONEMEP-38). */
public final class DesignTaskDto {
  private DesignTaskDto() {}

  /**
   * Partial update: every field is optional, and only those present are applied.
   *
   * <p>{@code clearOwner}, {@code clearStartDate} and {@code clearDueDate} exist because a null
   * field means "leave alone" here — without them there would be no way to express "unassign the
   * owner" or "remove the due date". Status and Source are absent entirely: ONEMEP-38 makes both
   * read-only on this screen, so the payload cannot ask for them.
   */
  public record UpdateRequest(
      Long ownerId,
      Boolean clearOwner,
      TaskPriority priority,
      Integer completionPct,
      LocalDate startDate,
      Boolean clearStartDate,
      LocalDate dueDate,
      Boolean clearDueDate,
      TaskReminder reminder) {}

  public record AddTagRequest(
      @NotBlank(message = "Enter a valid Tag.")
          @Size(max = 50, message = "Tag must not exceed 50 characters.")
          String label) {}

  public record TagView(Long id, String label) {}

  public record OwnerView(Long id, String displayName) {}

  /**
   * @param durationDays null when either date is missing — ONEMEP-38 requires a dash rather than a
   *     fabricated duration
   * @param reminderDate the resolved date the reminder falls on, recalculated from the current Due
   *     Date rather than stored
   */
  public record View(
      OwnerView owner,
      DesignStatus status,
      TaskPriority priority,
      Integer completionPct,
      List<TagView> tags,
      LocalDate startDate,
      LocalDate dueDate,
      Long durationDays,
      TaskReminder reminder,
      LocalDate reminderDate,
      DesignSource source) {}
}
