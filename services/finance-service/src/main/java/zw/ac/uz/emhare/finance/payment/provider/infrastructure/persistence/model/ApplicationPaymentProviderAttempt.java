package zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPaymentReference;
import zw.ac.uz.emhare.finance.payment.provider.*;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "application_payment_provider_attempts")
public class ApplicationPaymentProviderAttempt extends AuditableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_reference_id", nullable = false)
  private ApplicationPaymentReference paymentReference;

  @Column(name = "source_application_id", nullable = false)
  private java.util.UUID sourceApplicationId;

  @Column(name = "provider_code", nullable = false, length = 50)
  private String providerCode;

  @Column(name = "merchant_trace", nullable = false, length = 64)
  private String merchantTrace;

  @Column(name = "merchant_reference", nullable = false, length = 80)
  private String merchantReference;

  @Column(name = "return_nonce_hash", nullable = false, length = 64)
  private String returnNonceHash;

  @Column(name = "transaction_currency_code", nullable = false, length = 3)
  private String transactionCurrencyCode;

  @Column(name = "transaction_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal transactionAmount;

  @Column(name = "gateway_url", nullable = false, length = 500)
  private String gatewayUrl;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "provider_transaction_reference", length = 160)
  private String providerTransactionReference;

  @Column(name = "provider_status_code", length = 30)
  private String providerStatusCode;

  @Column(name = "provider_result_description", length = 500)
  private String providerResultDescription;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected ApplicationPaymentProviderAttempt() {}

  public ApplicationPaymentProviderAttempt(
      ApplicationPaymentReference paymentReference,
      String providerCode,
      String merchantTrace,
      String merchantReference,
      String returnNonceHash,
      String transactionCurrencyCode,
      BigDecimal transactionAmount,
      String gatewayUrl,
      Instant expiresAt) {
    this.paymentReference = paymentReference;
    this.sourceApplicationId = paymentReference.getSourceApplicationId();
    this.providerCode = providerCode;
    this.merchantTrace = merchantTrace;
    this.merchantReference = merchantReference;
    this.returnNonceHash = returnNonceHash;
    this.transactionCurrencyCode = transactionCurrencyCode;
    this.transactionAmount = transactionAmount;
    this.gatewayUrl = gatewayUrl;
    this.status = "INITIATED";
    this.expiresAt = expiresAt;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public java.util.UUID getSourceApplicationId() {
    return sourceApplicationId;
  }

  public String getMerchantTrace() {
    return merchantTrace;
  }

  public String getMerchantReference() {
    return merchantReference;
  }

  public String getReturnNonceHash() {
    return returnNonceHash;
  }

  public String getTransactionCurrencyCode() {
    return transactionCurrencyCode;
  }

  public BigDecimal getTransactionAmount() {
    return transactionAmount;
  }

  public String getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void recordProviderReturn(
      String returnStatus,
      String providerTransactionReference,
      String providerStatusCode,
      String providerResultDescription,
      Instant completedAt) {
    if (!"INITIATED".equals(status)) {
      return;
    }
    this.status = requireLength(returnStatus, 30, "Return status");
    this.providerTransactionReference =
        optionalLength(providerTransactionReference, 160, "Provider transaction reference");
    this.providerStatusCode = optionalLength(providerStatusCode, 30, "Provider status code");
    this.providerResultDescription =
        optionalLength(providerResultDescription, 500, "Provider result description");
    this.completedAt = completedAt;
  }

  private String requireLength(String value, int maximumLength, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
    return optionalLength(value, maximumLength, fieldName);
  }

  private String optionalLength(String value, int maximumLength, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmedValue = value.trim();
    if (trimmedValue.length() > maximumLength) {
      throw new IllegalArgumentException(fieldName + " exceeds " + maximumLength + " characters.");
    }
    return trimmedValue;
  }
}
