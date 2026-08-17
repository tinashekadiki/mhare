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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.common.messaging.ApplicationPaymentReferenceUpdatedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "application_payment_references",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_application_payment_references_reference",
            columnNames = "reference"))
public class ApplicationPaymentReference extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private Application application;

  @Column(nullable = false, length = 80)
  private String reference;

  @Column(name = "finance_payment_reference_id")
  private UUID financePaymentReferenceId;

  @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
  private BigDecimal amountDue;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  @Column(name = "base_currency_code", nullable = false, length = 3)
  private String baseCurrencyCode;

  @Column(name = "exchange_rate_id")
  private UUID exchangeRateId;

  @Column(name = "base_amount_due", precision = 12, scale = 2)
  private BigDecimal baseAmountDue;

  @Column(name = "rating_status", nullable = false, length = 20)
  private String ratingStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentReferenceStatus status;

  @Column(name = "required_for_submission", nullable = false)
  private boolean requiredForSubmission;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "finance_state_sequence", nullable = false)
  private long financeStateSequence;

  @Column(name = "last_finance_event_at")
  private Instant lastFinanceEventAt;

  protected ApplicationPaymentReference() {}

  public ApplicationPaymentReference(
      Application application, ApplicationPaymentReferenceUpdatedEvent financePaymentReference) {
    this.application = application;
    synchronize(financePaymentReference);
  }

  public boolean synchronize(ApplicationPaymentReferenceUpdatedEvent financePaymentReference) {
    if (!application.getId().equals(financePaymentReference.applicationId())) {
      throw new IllegalArgumentException(
          "Finance payment reference belongs to a different application.");
    }
    if (financePaymentReference.stateSequence() < financeStateSequence) {
      return false;
    }
    if (financePaymentReferenceId != null
        && !financePaymentReferenceId.equals(financePaymentReference.financePaymentReferenceId())) {
      throw new IllegalStateException(
          "Finance changed the immutable payment reference identifier.");
    }
    this.financePaymentReferenceId = financePaymentReference.financePaymentReferenceId();
    this.reference = financePaymentReference.reference();
    this.amountDue = financePaymentReference.amountDue();
    this.currencyCode = financePaymentReference.currencyCode();
    this.baseCurrencyCode = financePaymentReference.baseCurrencyCode();
    this.exchangeRateId = financePaymentReference.exchangeRateId();
    this.baseAmountDue = financePaymentReference.baseAmountDue();
    this.ratingStatus = financePaymentReference.ratingStatus();
    this.status = PaymentReferenceStatus.valueOf(financePaymentReference.status());
    this.requiredForSubmission = financePaymentReference.requiredForSubmission();
    this.paidAt = financePaymentReference.paidAt();
    this.financeStateSequence = financePaymentReference.stateSequence();
    this.lastFinanceEventAt = financePaymentReference.occurredAt();
    return true;
  }

  public UUID getFinancePaymentReferenceId() {
    return financePaymentReferenceId;
  }

  public Application getApplication() {
    return application;
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

  public BigDecimal getBaseAmountDue() {
    return baseAmountDue;
  }

  public String getRatingStatus() {
    return ratingStatus;
  }

  public PaymentReferenceStatus getStatus() {
    return status;
  }

  public boolean isRequiredForSubmission() {
    return requiredForSubmission;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
