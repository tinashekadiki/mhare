package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Audited comparison and approval record for an identity-document name mismatch. @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "applicant_identity_name_corrections",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_identity_name_corrections_application_document",
            columnNames = {"application_id", "document_id"}))
public class ApplicantIdentityNameCorrection extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private Application application;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_id", nullable = false)
  private Applicant applicant;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "registered_first_name", nullable = false, length = 100)
  private String registeredFirstName;

  @Column(name = "registered_middle_names", length = 150)
  private String registeredMiddleNames;

  @Column(name = "registered_last_name", nullable = false, length = 100)
  private String registeredLastName;

  @Column(name = "document_first_name", nullable = false, length = 100)
  private String documentFirstName;

  @Column(name = "document_middle_names", length = 150)
  private String documentMiddleNames;

  @Column(name = "document_last_name", nullable = false, length = 100)
  private String documentLastName;

  @Column(name = "request_reason", length = 1000)
  private String requestReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private IdentityNameCorrectionStatus status;

  @Column(name = "requested_at")
  private Instant requestedAt;

  @Column(name = "requested_by_user_id")
  private UUID requestedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decision_reason", length = 1000)
  private String decisionReason;

  @Column(name = "core_synchronized_at")
  private Instant coreSynchronizedAt;

  protected ApplicantIdentityNameCorrection() {}

  public ApplicantIdentityNameCorrection(
      Application application,
      UUID documentId,
      String documentFirstName,
      String documentMiddleNames,
      String documentLastName) {
    this.application = java.util.Objects.requireNonNull(application, "Application is required.");
    this.applicant = application.getApplicant();
    this.documentId = java.util.Objects.requireNonNull(documentId, "Document is required.");
    this.registeredFirstName = applicant.getFirstName();
    this.registeredMiddleNames = applicant.getMiddleNames();
    this.registeredLastName = applicant.getLastName();
    reviewOcrReading(documentFirstName, documentMiddleNames, documentLastName);
  }

  public void reviewOcrReading(String firstName, String middleNames, String lastName) {
    if (status == IdentityNameCorrectionStatus.REQUESTED
        || status == IdentityNameCorrectionStatus.APPROVED) {
      throw new IllegalStateException("A submitted name-correction request cannot be edited.");
    }
    documentFirstName = required(firstName, "Document first name");
    documentMiddleNames = optional(middleNames);
    documentLastName = required(lastName, "Document last name");
    status = IdentityNameCorrectionStatus.OCR_REVIEWED;
    requestReason = null;
    requestedAt = null;
    requestedByUserId = null;
    decidedAt = null;
    decidedByUserId = null;
    decisionReason = null;
  }

  public void request(UUID applicantUserId, String reason, Instant now) {
    if (status == IdentityNameCorrectionStatus.APPROVED) {
      throw new IllegalStateException("The official-name correction is already approved.");
    }
    requestReason = required(reason, "Correction reason");
    status = IdentityNameCorrectionStatus.REQUESTED;
    requestedAt = java.util.Objects.requireNonNull(now, "Request time is required.");
    requestedByUserId =
        java.util.Objects.requireNonNull(applicantUserId, "Applicant user is required.");
    decidedAt = null;
    decidedByUserId = null;
    decisionReason = null;
  }

  public void approve(UUID staffUserId, String reason, Instant now) {
    requireRequested();
    status = IdentityNameCorrectionStatus.APPROVED;
    decidedByUserId = java.util.Objects.requireNonNull(staffUserId, "Decision user is required.");
    decisionReason = required(reason, "Approval reason");
    decidedAt = java.util.Objects.requireNonNull(now, "Decision time is required.");
    coreSynchronizedAt = now;
  }

  public void reject(UUID staffUserId, String reason, Instant now) {
    requireRequested();
    status = IdentityNameCorrectionStatus.REJECTED;
    decidedByUserId = java.util.Objects.requireNonNull(staffUserId, "Decision user is required.");
    decisionReason = required(reason, "Rejection reason");
    decidedAt = java.util.Objects.requireNonNull(now, "Decision time is required.");
  }

  public void supersede() {
    if (status == IdentityNameCorrectionStatus.APPROVED) {
      throw new IllegalStateException("An approved identity-name correction cannot be superseded.");
    }
    status = IdentityNameCorrectionStatus.SUPERSEDED;
  }

  private void requireRequested() {
    if (status != IdentityNameCorrectionStatus.REQUESTED) {
      throw new IllegalStateException("Only a requested official-name correction can be decided.");
    }
  }

  private String required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
    return value.trim();
  }

  private String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public Application getApplication() {
    return application;
  }

  public Applicant getApplicant() {
    return applicant;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getRegisteredFirstName() {
    return registeredFirstName;
  }

  public String getRegisteredMiddleNames() {
    return registeredMiddleNames;
  }

  public String getRegisteredLastName() {
    return registeredLastName;
  }

  public String getDocumentFirstName() {
    return documentFirstName;
  }

  public String getDocumentMiddleNames() {
    return documentMiddleNames;
  }

  public String getDocumentLastName() {
    return documentLastName;
  }

  public String getRequestReason() {
    return requestReason;
  }

  public IdentityNameCorrectionStatus getStatus() {
    return status;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public UUID getRequestedByUserId() {
    return requestedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public Instant getCoreSynchronizedAt() {
    return coreSynchronizedAt;
  }
}
