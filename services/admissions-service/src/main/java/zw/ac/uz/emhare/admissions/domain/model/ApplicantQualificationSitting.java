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

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "applicant_qualification_sittings")
public class ApplicantQualificationSitting extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private Application application;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private QualificationLevel level;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exam_body_id")
  private ExamBody examBody;

  @Column(name = "institution_name", length = 200)
  private String institutionName;

  @Column(name = "centre_number", length = 50)
  private String centreNumber;

  @Column(name = "candidate_number", length = 50)
  private String candidateNumber;

  @Column(name = "year_written")
  private Integer yearWritten;

  @Column(name = "duration_months")
  private Integer durationMonths;

  @Column(name = "country_id")
  private UUID countryId;

  @Column(name = "document_id")
  private UUID documentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "award_type_code", length = 30)
  private QualificationAwardType awardType;

  @Column(name = "qualification_name", length = 200)
  private String qualificationName;

  @Column(name = "legacy_source_table", length = 100)
  private String legacySourceTable;

  @Column(name = "legacy_source_id")
  private Long legacySourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 30)
  private QualificationResultStatus verificationStatus;

  @Column(name = "verified_by_user_id")
  private UUID verifiedByUserId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  protected ApplicantQualificationSitting() {}

  public ApplicantQualificationSitting(
      Application application,
      QualificationLevel level,
      ExamBody examBody,
      String centreNumber,
      String candidateNumber,
      Integer yearWritten) {
    this.application = application;
    this.level = level;
    this.examBody = examBody;
    this.centreNumber = centreNumber;
    this.candidateNumber = candidateNumber;
    this.yearWritten = yearWritten;
    this.verificationStatus = QualificationResultStatus.CAPTURED;
  }

  public ApplicantQualificationSitting(
      Application application,
      QualificationLevel level,
      ExamBody examBody,
      String institutionName,
      String centreNumber,
      String candidateNumber,
      Integer yearWritten,
      UUID countryId,
      UUID documentId) {
    this(application, level, examBody, centreNumber, candidateNumber, yearWritten);
    this.institutionName = optional(institutionName);
    this.countryId = countryId;
    this.documentId = documentId;
  }

  public void update(
      ExamBody examBody,
      String institutionName,
      String centreNumber,
      String candidateNumber,
      Integer yearWritten,
      UUID countryId,
      UUID documentId,
      Integer durationMonths) {
    requireEditable();
    this.examBody = examBody;
    this.institutionName = optional(institutionName);
    this.centreNumber = optional(centreNumber);
    this.candidateNumber = optional(candidateNumber);
    this.yearWritten = yearWritten;
    this.countryId = countryId;
    this.documentId = documentId;
    this.durationMonths = requireDuration(durationMonths);
    this.verificationStatus = QualificationResultStatus.CAPTURED;
    this.rejectionReason = null;
  }

  public void updateAwardDetails(QualificationAwardType awardType, String qualificationName) {
    requireEditable();
    this.awardType = awardType;
    this.qualificationName = optional(qualificationName);
  }

  public void verify(UUID actorUserId, Instant now) {
    verificationStatus = QualificationResultStatus.VERIFIED;
    verifiedByUserId = actorUserId;
    verifiedAt = now;
    rejectionReason = null;
  }

  public void reject(UUID actorUserId, String reason, Instant now) {
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("Qualification rejection reason is required.");
    verificationStatus = QualificationResultStatus.REJECTED;
    verifiedByUserId = actorUserId;
    verifiedAt = now;
    rejectionReason = reason.trim();
  }

  public void reopenForApplicantCorrection() {
    verificationStatus = QualificationResultStatus.CAPTURED;
    verifiedByUserId = null;
    verifiedAt = null;
    rejectionReason = null;
  }

  private void requireEditable() {
    if (verificationStatus == QualificationResultStatus.VERIFIED) {
      throw new IllegalStateException("A verified qualification sitting cannot be edited.");
    }
  }

  public Application getApplication() {
    return application;
  }

  public QualificationLevel getLevel() {
    return level;
  }

  public ExamBody getExamBody() {
    return examBody;
  }

  public String getInstitutionName() {
    return institutionName;
  }

  public String getCentreNumber() {
    return centreNumber;
  }

  public String getCandidateNumber() {
    return candidateNumber;
  }

  public Integer getYearWritten() {
    return yearWritten;
  }

  public Integer getDurationMonths() {
    return durationMonths;
  }

  public boolean requiresSubjectResultsForVerification() {
    return level == QualificationLevel.O_LEVEL || level == QualificationLevel.A_LEVEL;
  }

  public boolean hasCompleteEvidence(long subjectResultCount) {
    if (documentId == null) return false;
    if (requiresSubjectResultsForVerification()) {
      return subjectResultCount > 0;
    }
    return durationMonths != null && institutionName != null && qualificationName != null;
  }

  public UUID getCountryId() {
    return countryId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public QualificationAwardType getAwardType() {
    return awardType;
  }

  public String getQualificationName() {
    return qualificationName;
  }

  public QualificationResultStatus getVerificationStatus() {
    return verificationStatus;
  }

  public UUID getVerifiedByUserId() {
    return verifiedByUserId;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Integer requireDuration(Integer durationMonths) {
    if (durationMonths != null && durationMonths < 1) {
      throw new IllegalArgumentException("Qualification duration must be at least one month.");
    }
    return durationMonths;
  }
}
