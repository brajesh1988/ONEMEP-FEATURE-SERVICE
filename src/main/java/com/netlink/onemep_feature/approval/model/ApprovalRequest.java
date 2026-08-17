package com.netlink.onemep_feature.approval.model;

import com.netlink.onemep_feature.common.model.BaseEntity;
import com.netlink.onemep_feature.design.model.Design;
import com.netlink.onemep_feature.file.model.DesignFile;
import com.netlink.onemep_feature.file.model.DesignFileVersion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * One approval cycle for one revision of one logical file (ONEMEP-40).
 *
 * <p>{@link #file} and {@link #version} are both held, and both are {@code updatable = false}: the
 * request belongs to the file, but the decision was made about a specific revision, and neither
 * association may drift once the request exists.
 */
@Entity
@Table(name = "approval_request")
@Getter
@Setter
public class ApprovalRequest extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "design_id", nullable = false, updatable = false)
  private Design design;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "file_id", nullable = false, updatable = false)
  private DesignFile file;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, updatable = false)
  private DesignFileVersion version;

  @Column(name = "requester_id", nullable = false, updatable = false)
  private Long requesterId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApprovalStatus status = ApprovalStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_stage", nullable = false, length = 20)
  private ApprovalStage currentStage = ApprovalStage.INITIAL;

  @Column(name = "route_to_principal", nullable = false, updatable = false)
  private Boolean routeToPrincipal = Boolean.FALSE;

  @Column(name = "is_resubmission", nullable = false, updatable = false)
  private Boolean resubmission = Boolean.FALSE;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  /** Detects the recall-versus-decide race; the loser is told to refresh. */
  @Version
  @Column(name = "version", nullable = false)
  private Integer lockVersion;

  @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ApprovalAssignee> assignees = new ArrayList<>();

  public void addAssignee(ApprovalAssignee assignee) {
    assignee.setRequest(this);
    assignees.add(assignee);
  }

  /** Live assignees for the stage currently outstanding. */
  public List<ApprovalAssignee> activeAssignees(ApprovalStage stage) {
    return assignees.stream()
        .filter(a -> Boolean.TRUE.equals(a.getActive()) && a.getStage() == stage)
        .toList();
  }

  /** True once every live assignee at {@link #currentStage} has approved. */
  public boolean stageComplete() {
    List<ApprovalAssignee> live = activeAssignees(currentStage);
    return !live.isEmpty()
        && live.stream().allMatch(a -> a.getDecision() == ApproverDecision.APPROVED);
  }

  /** Whether anybody has answered yet — the condition that makes a recall no longer possible. */
  public boolean anyDecisionTaken() {
    return assignees.stream().anyMatch(a -> a.getDecision() != ApproverDecision.PENDING);
  }
}
