package zw.ac.uz.emhare.finance.payment.domain.model;

import zw.ac.uz.emhare.finance.payment.*;

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
import java.util.Locale;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "application_payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_payments_provider_transaction",
                columnNames = {"provider_code", "provider_transaction_reference"}))
public class ApplicationPayment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_reference_id", nullable = false)
    private ApplicationPaymentReference paymentReference;

    @Column(name = "source_application_id", nullable = false)
    private java.util.UUID sourceApplicationId;

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "provider_transaction_reference", nullable = false, length = 160)
    private String providerTransactionReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_rate_id")
    private ExchangeRate exchangeRate;

    @Column(name = "base_amount", precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_status", nullable = false, length = 20)
    private MoneyRatingStatus ratingStatus;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationPaymentStatus status;

    @Column(name = "provider_event_fingerprint", nullable = false, length = 128)
    private String providerEventFingerprint;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected ApplicationPayment() {
    }

    public ApplicationPayment(
            ApplicationPaymentReference paymentReference,
            String providerCode,
            String providerTransactionReference,
            BigDecimal amount,
            String currencyCode,
            ExchangeRate exchangeRate,
            BigDecimal baseAmount,
            MoneyRatingStatus ratingStatus,
            Instant paidAt,
            Instant confirmedAt,
            String providerEventFingerprint) {
        this.paymentReference = paymentReference;
        this.sourceApplicationId = paymentReference.getSourceApplicationId();
        this.providerCode = providerCode.trim().toUpperCase(Locale.ROOT);
        this.providerTransactionReference = providerTransactionReference.trim();
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.baseCurrencyCode = "USD";
        this.exchangeRate = exchangeRate;
        this.baseAmount = baseAmount;
        this.ratingStatus = ratingStatus;
        this.paidAt = paidAt;
        this.confirmedAt = confirmedAt;
        this.status = ApplicationPaymentStatus.CONFIRMED;
        this.providerEventFingerprint = providerEventFingerprint;
    }

    public boolean matches(
            java.util.UUID sourceApplicationId,
            BigDecimal amount,
            String currencyCode,
            String providerEventFingerprint) {
        return this.sourceApplicationId.equals(sourceApplicationId)
                && this.amount.compareTo(amount) == 0
                && this.currencyCode.equals(currencyCode)
                && this.providerEventFingerprint.equals(providerEventFingerprint);
    }

    public ApplicationPaymentStatus getStatus() {
        return status;
    }

    public MoneyRatingStatus getRatingStatus() {
        return ratingStatus;
    }
}
