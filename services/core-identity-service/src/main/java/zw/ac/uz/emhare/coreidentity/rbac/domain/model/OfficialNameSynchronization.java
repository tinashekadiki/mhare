package zw.ac.uz.emhare.coreidentity.rbac.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Core-owned audit evidence for an approved external official-name synchronization. @author Tinashe
 * K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "official_name_synchronizations",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_official_name_synchronizations_source_request",
            columnNames = "source_request_id"))
public class OfficialNameSynchronization extends AuditableEntity {

  @Column(name = "source_request_id", nullable = false)
  private UUID sourceRequestId;

  @Column(name = "source_application_id", nullable = false)
  private UUID sourceApplicationId;

  @Column(name = "source_document_id", nullable = false)
  private UUID sourceDocumentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private PlatformUser user;

  @Column(name = "previous_first_name", length = 100)
  private String previousFirstName;

  @Column(name = "previous_middle_names", length = 150)
  private String previousMiddleNames;

  @Column(name = "previous_last_name", length = 100)
  private String previousLastName;

  @Column(name = "approved_first_name", nullable = false, length = 100)
  private String approvedFirstName;

  @Column(name = "approved_middle_names", length = 150)
  private String approvedMiddleNames;

  @Column(name = "approved_last_name", nullable = false, length = 100)
  private String approvedLastName;

  @Column(name = "approval_reason", nullable = false, length = 1000)
  private String approvalReason;

  @Column(name = "synchronized_at", nullable = false)
  private Instant synchronizedAt;

  @Column(name = "synchronized_by_user_id", nullable = false)
  private UUID synchronizedByUserId;

  protected OfficialNameSynchronization() {}

  public OfficialNameSynchronization(
      UUID sourceRequestId,
      UUID sourceApplicationId,
      UUID sourceDocumentId,
      PlatformUser user,
      String approvedFirstName,
      String approvedMiddleNames,
      String approvedLastName,
      String approvalReason,
      Instant synchronizedAt,
      UUID synchronizedByUserId) {
    this.sourceRequestId = java.util.Objects.requireNonNull(sourceRequestId);
    this.sourceApplicationId = java.util.Objects.requireNonNull(sourceApplicationId);
    this.sourceDocumentId = java.util.Objects.requireNonNull(sourceDocumentId);
    this.user = java.util.Objects.requireNonNull(user);
    this.previousFirstName = user.getFirstName();
    this.previousMiddleNames = user.getMiddleNames();
    this.previousLastName = user.getLastName();
    this.approvedFirstName = required(approvedFirstName, "Approved first name");
    this.approvedMiddleNames = optional(approvedMiddleNames);
    this.approvedLastName = required(approvedLastName, "Approved last name");
    this.approvalReason = required(approvalReason, "Approval reason");
    this.synchronizedAt = java.util.Objects.requireNonNull(synchronizedAt);
    this.synchronizedByUserId = java.util.Objects.requireNonNull(synchronizedByUserId);
  }

  private String required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
    return value.trim();
  }

  private String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getSourceRequestId() {
    return sourceRequestId;
  }

  public UUID getSourceApplicationId() {
    return sourceApplicationId;
  }

  public UUID getSourceDocumentId() {
    return sourceDocumentId;
  }

  public PlatformUser getUser() {
    return user;
  }

  public String getApprovedFirstName() {
    return approvedFirstName;
  }

  public String getApprovedMiddleNames() {
    return approvedMiddleNames;
  }

  public String getApprovedLastName() {
    return approvedLastName;
  }

  public String getApprovalReason() {
    return approvalReason;
  }

  public Instant getSynchronizedAt() {
    return synchronizedAt;
  }

  public UUID getSynchronizedByUserId() {
    return synchronizedByUserId;
  }
}
