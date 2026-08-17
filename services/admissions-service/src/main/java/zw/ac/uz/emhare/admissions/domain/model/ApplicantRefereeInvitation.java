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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Secure invitation and confidential response supplied by an applicant-nominated referee. @author
 * Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "applicant_referee_invitations")
public class ApplicantRefereeInvitation extends AuditableEntity {

  public enum Status {
    SENT,
    OPENED,
    SUBMITTED,
    REVOKED,
    EXPIRED
  }

  public enum Recommendation {
    STRONGLY_RECOMMEND,
    RECOMMEND,
    RECOMMEND_WITH_RESERVATIONS,
    DO_NOT_RECOMMEND
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private Application application;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "referee_id", nullable = false)
  private ApplicantReferee referee;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "nomination_id")
  private ApplicationRefereeNomination nomination;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(name = "token_hint", nullable = false, length = 12)
  private String tokenHint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "opened_at")
  private Instant openedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "send_count", nullable = false)
  private int sendCount;

  @Column(name = "relationship_to_applicant", length = 200)
  private String relationshipToApplicant;

  @Column(name = "years_known")
  private Integer yearsKnown;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private Recommendation recommendation;

  @Column(length = 5000)
  private String comments;

  @Column(name = "declaration_accepted", nullable = false)
  private boolean declarationAccepted;

  protected ApplicantRefereeInvitation() {}

  public ApplicantRefereeInvitation(
      Application application,
      ApplicantReferee referee,
      String tokenHash,
      String tokenHint,
      Instant sentAt,
      Instant expiresAt,
      int sendCount) {
    this(application, referee, null, tokenHash, tokenHint, sentAt, expiresAt, sendCount);
  }

  public ApplicantRefereeInvitation(
      Application application,
      ApplicantReferee referee,
      ApplicationRefereeNomination nomination,
      String tokenHash,
      String tokenHint,
      Instant sentAt,
      Instant expiresAt,
      int sendCount) {
    this.application = application;
    this.referee = referee;
    this.nomination = nomination;
    this.tokenHash = required(tokenHash, "Token hash");
    this.tokenHint = required(tokenHint, "Token hint");
    this.sentAt = sentAt;
    this.expiresAt = expiresAt;
    this.sendCount = sendCount;
    this.status = Status.SENT;
  }

  public void markOpened(Instant openedAt) {
    if (status == Status.SENT) {
      status = Status.OPENED;
      this.openedAt = openedAt;
    }
  }

  public void submit(
      String relationshipToApplicant,
      int yearsKnown,
      Recommendation recommendation,
      String comments,
      boolean declarationAccepted,
      Instant submittedAt) {
    if (status == Status.SUBMITTED) {
      throw new IllegalStateException("This reference has already been submitted.");
    }
    if (status == Status.REVOKED || status == Status.EXPIRED) {
      throw new IllegalStateException("This reference invitation is no longer active.");
    }
    if (yearsKnown < 0 || yearsKnown > 100) {
      throw new IllegalArgumentException("Years known must be between 0 and 100.");
    }
    if (!declarationAccepted) {
      throw new IllegalArgumentException(
          "Confirm that this confidential reference is accurate before submitting.");
    }
    this.relationshipToApplicant = required(relationshipToApplicant, "Relationship to applicant");
    this.yearsKnown = yearsKnown;
    this.recommendation = recommendation;
    this.comments = required(comments, "Reference comments");
    this.declarationAccepted = true;
    this.submittedAt = submittedAt;
    this.status = Status.SUBMITTED;
    if (openedAt == null) this.openedAt = submittedAt;
  }

  public void revoke() {
    if (status == Status.SENT || status == Status.OPENED) status = Status.REVOKED;
  }

  public void expire(Instant expiredAt) {
    if ((status == Status.SENT || status == Status.OPENED) && !expiresAt.isAfter(expiredAt)) {
      status = Status.EXPIRED;
    }
  }

  public Application getApplication() {
    return application;
  }

  public ApplicantReferee getReferee() {
    return referee;
  }

  public ApplicationRefereeNomination getNomination() {
    return nomination;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getTokenHint() {
    return tokenHint;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public int getSendCount() {
    return sendCount;
  }

  public String getRelationshipToApplicant() {
    return relationshipToApplicant;
  }

  public Integer getYearsKnown() {
    return yearsKnown;
  }

  public Recommendation getRecommendation() {
    return recommendation;
  }

  public String getComments() {
    return comments;
  }

  public boolean isDeclarationAccepted() {
    return declarationAccepted;
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
    return value.trim();
  }
}
