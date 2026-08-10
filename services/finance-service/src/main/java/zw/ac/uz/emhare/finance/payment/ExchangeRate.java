package zw.ac.uz.emhare.finance.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate extends AuditableEntity {

    @Column(name = "source_currency_code", nullable = false, length = 3)
    private String sourceCurrencyCode;

    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode;

    @Column(name = "rate_to_base", nullable = false, precision = 20, scale = 8)
    private BigDecimal rateToBase;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "source_name", nullable = false, length = 120)
    private String sourceName;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeRateStatus status;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "prepared_by_user_id", nullable = false)
    private UUID preparedByUserId;

    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;

    @Column(name = "retired_by_user_id")
    private UUID retiredByUserId;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retirement_reason", length = 1000)
    private String retirementReason;

    protected ExchangeRate() {
    }

    public ExchangeRate(
            String sourceCurrencyCode,
            BigDecimal rateToBase,
            Instant effectiveFrom,
            Instant effectiveTo,
            String sourceName,
            String sourceReference,
            UUID preparedByUserId) {
        this.sourceCurrencyCode = normalizeCurrencyCode(sourceCurrencyCode);
        this.baseCurrencyCode = "USD";
        if ("USD".equals(this.sourceCurrencyCode)) {
            throw new IllegalArgumentException("An exchange rate is not required for USD base-currency transactions.");
        }
        if (rateToBase == null || rateToBase.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than zero.");
        }
        if (effectiveFrom == null || effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Exchange-rate effective dates are invalid.");
        }
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Exchange-rate source is required.");
        }
        this.rateToBase = rateToBase;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.sourceName = sourceName.trim();
        this.sourceReference = sourceReference == null ? null : sourceReference.trim();
        this.preparedByUserId = java.util.Objects.requireNonNull(preparedByUserId, "Exchange-rate preparer is required.");
        this.status = ExchangeRateStatus.DRAFT;
    }

    public void approve(UUID actorUserId, Instant approvedAt, String reason) {
        if (status != ExchangeRateStatus.DRAFT) {
            throw new IllegalStateException("Only a draft exchange rate can be approved.");
        }
        if (actorUserId == null || approvedAt == null) {
            throw new IllegalArgumentException("Exchange-rate approval actor and timestamp are required.");
        }
        if (actorUserId.equals(preparedByUserId)) {
            throw new IllegalStateException("Exchange-rate approval requires a different Finance operator.");
        }
        this.approvedByUserId = actorUserId;
        this.approvedAt = approvedAt;
        this.approvalReason = requireReason(reason, "Exchange-rate approval reason");
        this.status = ExchangeRateStatus.ACTIVE;
    }

    public void retire(UUID actorUserId, Instant retiredAt, String reason) {
        if (status != ExchangeRateStatus.ACTIVE) {
            throw new IllegalStateException("Only an active exchange rate can be retired.");
        }
        this.retiredByUserId = java.util.Objects.requireNonNull(actorUserId, "Exchange-rate retirement actor is required.");
        this.retiredAt = java.util.Objects.requireNonNull(retiredAt, "Exchange-rate retirement timestamp is required.");
        this.retirementReason = requireReason(reason, "Exchange-rate retirement reason");
        this.status = ExchangeRateStatus.RETIRED;
    }

    public BigDecimal getRateToBase() {
        return rateToBase;
    }

    public String getSourceCurrencyCode() {
        return sourceCurrencyCode;
    }

    public String getBaseCurrencyCode() { return baseCurrencyCode; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public String getSourceName() { return sourceName; }
    public String getSourceReference() { return sourceReference; }
    public String getStatus() { return status.name(); }
    public UUID getPreparedByUserId() { return preparedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getRetiredByUserId() { return retiredByUserId; }
    public Instant getRetiredAt() { return retiredAt; }

    private static String requireReason(String reason, String label) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return reason.trim();
    }

    public static String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required.");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Currency code must contain three letters.");
        }
        return normalized;
    }
}
