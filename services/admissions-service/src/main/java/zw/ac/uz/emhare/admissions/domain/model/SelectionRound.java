package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "selection_rounds")
public class SelectionRound extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "admission_cycle_id", nullable = false)
  private AdmissionCycle admissionCycle;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 180)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SelectionRoundStatus status;

  @Column(name = "opened_at")
  private Instant openedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "closed_at")
  private Instant closedAt;

  protected SelectionRound() {}

  public SelectionRound(AdmissionCycle admissionCycle, String code, String name) {
    this.admissionCycle = admissionCycle;
    this.code = requireText(code, "Selection round code");
    this.name = requireText(name, "Selection round name");
    this.status = SelectionRoundStatus.DRAFT;
  }

  public void open(Instant now) {
    requireStatus(SelectionRoundStatus.DRAFT, "Only a draft selection round can be opened.");
    status = SelectionRoundStatus.OPEN;
    openedAt = now;
  }

  public void approve(UUID actorUserId, Instant now) {
    requireStatus(SelectionRoundStatus.OPEN, "Only an open selection round can be approved.");
    status = SelectionRoundStatus.APPROVED;
    approvedByUserId = actorUserId;
    approvedAt = now;
  }

  public void close(Instant now) {
    if (status != SelectionRoundStatus.OPEN && status != SelectionRoundStatus.APPROVED) {
      throw new IllegalStateException("Only an open or approved selection round can be closed.");
    }
    status = SelectionRoundStatus.CLOSED;
    closedAt = now;
  }

  private void requireStatus(SelectionRoundStatus required, String message) {
    if (status != required) {
      throw new IllegalStateException(message);
    }
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value.trim();
  }

  public AdmissionCycle getAdmissionCycle() {
    return admissionCycle;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public SelectionRoundStatus getStatus() {
    return status;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public UUID getApprovedByUserId() {
    return approvedByUserId;
  }

  public Instant getClosedAt() {
    return closedAt;
  }
}
