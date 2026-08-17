package zw.ac.uz.emhare.finance.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.payment.*;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "application_payment_references",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_application_payment_references_application",
          columnNames = "source_application_id"),
      @UniqueConstraint(
          name = "uk_application_payment_references_reference",
          columnNames = "reference")
    })
public class ApplicationPaymentReference extends AuditableEntity {

  @Column(name = "source_application_id", nullable = false)
  private UUID sourceApplicationId;

  @Column(name = "applicant_user_id", nullable = false)
  private UUID applicantUserId;

  @Column(name = "applicant_keycloak_user_id", nullable = false)
  private UUID applicantKeycloakUserId;

  @Column(nullable = false, length = 80)
  private String reference;

  @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
  private BigDecimal amountDue;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  @Column(name = "base_currency_code", nullable = false, length = 3)
  private String baseCurrencyCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exchange_rate_id")
  private ExchangeRate exchangeRate;

  @Column(name = "base_amount_due", precision = 12, scale = 2)
  private BigDecimal baseAmountDue;

  @Enumerated(EnumType.STRING)
  @Column(name = "rating_status", nullable = false, length = 20)
  private MoneyRatingStatus ratingStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentReferenceStatus status;

  @Column(name = "required_for_submission", nullable = false)
  private boolean requiredForSubmission;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "state_sequence", nullable = false)
  private long stateSequence;

  protected ApplicationPaymentReference() {}

  public ApplicationPaymentReference(
      UUID sourceApplicationId,
      UUID applicantUserId,
      UUID applicantKeycloakUserId,
      String reference,
      BigDecimal amountDue,
      String currencyCode,
      ExchangeRate exchangeRate,
      BigDecimal baseAmountDue,
      MoneyRatingStatus ratingStatus,
      boolean requiredForSubmission,
      Instant expiresAt) {
    this.sourceApplicationId = sourceApplicationId;
    this.applicantUserId = applicantUserId;
    this.applicantKeycloakUserId = applicantKeycloakUserId;
    this.reference = reference;
    this.amountDue = amountDue;
    this.currencyCode = currencyCode;
    this.baseCurrencyCode = "USD";
    this.exchangeRate = exchangeRate;
    this.baseAmountDue = baseAmountDue;
    this.ratingStatus = ratingStatus;
    this.status = PaymentReferenceStatus.PENDING;
    this.requiredForSubmission = requiredForSubmission;
    this.expiresAt = expiresAt;
  }

  public void markPaid(
      Instant paidAt,
      ExchangeRate exchangeRate,
      BigDecimal baseAmountDue,
      MoneyRatingStatus ratingStatus) {
    if (status == PaymentReferenceStatus.PAID) {
      return;
    }
    if (status != PaymentReferenceStatus.PENDING) {
      throw new IllegalStateException("Only a pending payment reference can be paid.");
    }
    this.exchangeRate = exchangeRate;
    this.baseAmountDue = baseAmountDue;
    this.ratingStatus = ratingStatus;
    this.paidAt = paidAt;
    this.status = PaymentReferenceStatus.PAID;
    this.stateSequence++;
  }

  public boolean matches(UUID applicantUserId, BigDecimal amountDue, String currencyCode) {
    return this.applicantUserId.equals(applicantUserId)
        && this.amountDue.compareTo(amountDue) == 0
        && this.currencyCode.equals(currencyCode);
  }

  public boolean isOwnedByKeycloakUser(UUID keycloakUserId) {
    return applicantKeycloakUserId.equals(keycloakUserId);
  }

  public UUID getSourceApplicationId() {
    return sourceApplicationId;
  }

  public UUID getApplicantUserId() {
    return applicantUserId;
  }

  public String getReference() {
    return reference;
  }

  public BigDecimal getAmountDue() {
    return amountDue;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public String getBaseCurrencyCode() {
    return baseCurrencyCode;
  }

  public ExchangeRate getExchangeRate() {
    return exchangeRate;
  }

  public UUID getExchangeRateId() {
    return exchangeRate == null ? null : exchangeRate.getId();
  }

  public BigDecimal getBaseAmountDue() {
    return baseAmountDue;
  }

  public MoneyRatingStatus getRatingStatus() {
    return ratingStatus;
  }

  public String getRatingStatusCode() {
    return ratingStatus.name();
  }

  public PaymentReferenceStatus getStatus() {
    return status;
  }

  public String getStatusCode() {
    return status.name();
  }

  public boolean isWorkflowCleared() {
    return status == PaymentReferenceStatus.PAID && ratingStatus == MoneyRatingStatus.RATED;
  }

  public boolean isRequiredForSubmission() {
    return requiredForSubmission;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public long getStateSequence() {
    return stateSequence;
  }
}
