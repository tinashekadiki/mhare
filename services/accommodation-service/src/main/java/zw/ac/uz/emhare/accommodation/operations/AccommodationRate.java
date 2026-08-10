package zw.ac.uz.emhare.accommodation.operations;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.accommodation.setup.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.AccommodationRoomType;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_rates")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationRate extends AuditableEntity {
    public enum RatingStatus { RATED, UNRATED }
    public enum Status { DRAFT, ACTIVE, RETIRED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_period_id")
    private AccommodationApplicationPeriod applicationPeriod;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id")
    private AccommodationRoomType roomType;
    @Column(name = "rate_version", nullable = false) private int rateVersion;
    @Column(name = "finance_fee_catalogue_id", nullable = false) private UUID financeFeeCatalogueId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "transaction_currency_code", nullable = false, length = 3, columnDefinition = "char(3)") private String transactionCurrencyCode;
    @Column(name = "indicative_transaction_amount", nullable = false, precision = 19, scale = 4) private BigDecimal indicativeTransactionAmount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency_code", nullable = false, length = 3, columnDefinition = "char(3)") private String baseCurrencyCode;
    @Column(name = "exchange_rate_id") private UUID exchangeRateId;
    @Column(name = "indicative_base_amount", precision = 19, scale = 4) private BigDecimal indicativeBaseAmount;
    @Enumerated(EnumType.STRING) @Column(name = "rating_status", nullable = false, length = 20) private RatingStatus ratingStatus;
    @Column(name = "effective_from", nullable = false) private Instant effectiveFrom;
    @Column(name = "effective_until") private Instant effectiveUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "prepared_by_user_id", nullable = false) private UUID preparedByUserId;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000) private String approvalReason;

    protected AccommodationRate() {}

    public AccommodationRate(AccommodationApplicationPeriod applicationPeriod, AccommodationRoomType roomType,
            int rateVersion, UUID financeFeeCatalogueId, String transactionCurrencyCode,
            BigDecimal indicativeTransactionAmount, UUID exchangeRateId, BigDecimal indicativeBaseAmount,
            Instant effectiveFrom, Instant effectiveUntil, UUID preparedByUserId) {
        if (applicationPeriod == null || roomType == null || financeFeeCatalogueId == null
                || effectiveFrom == null || preparedByUserId == null) {
            throw new IllegalArgumentException("Period, room type, Finance fee, effective date, and preparing operator are required.");
        }
        if (rateVersion < 1) throw new IllegalArgumentException("Rate version must be positive.");
        if (indicativeTransactionAmount == null || indicativeTransactionAmount.signum() <= 0) {
            throw new IllegalArgumentException("Indicative transaction amount must be positive.");
        }
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Rate end time must be after its start time.");
        }
        this.applicationPeriod = applicationPeriod;
        this.roomType = roomType;
        this.rateVersion = rateVersion;
        this.financeFeeCatalogueId = financeFeeCatalogueId;
        this.transactionCurrencyCode = AccommodationValueRules.code(transactionCurrencyCode, "Transaction currency");
        this.indicativeTransactionAmount = indicativeTransactionAmount;
        this.baseCurrencyCode = "USD";
        applyRating(exchangeRateId, indicativeBaseAmount);
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.preparedByUserId = preparedByUserId;
        this.status = Status.DRAFT;
    }

    private void applyRating(UUID rateId, BigDecimal baseAmount) {
        if ("USD".equals(transactionCurrencyCode)) {
            if (rateId != null) throw new IllegalArgumentException("USD rates must not reference an exchange rate.");
            exchangeRateId = null;
            indicativeBaseAmount = indicativeTransactionAmount;
            ratingStatus = RatingStatus.RATED;
            return;
        }
        if (rateId == null) {
            if (baseAmount != null) throw new IllegalArgumentException("An unrated foreign-currency rate cannot have a USD base amount.");
            exchangeRateId = null;
            indicativeBaseAmount = null;
            ratingStatus = RatingStatus.UNRATED;
            return;
        }
        if (baseAmount == null || baseAmount.signum() <= 0) {
            throw new IllegalArgumentException("A positive USD base amount is required when an exchange rate is supplied.");
        }
        exchangeRateId = rateId;
        indicativeBaseAmount = baseAmount;
        ratingStatus = RatingStatus.RATED;
    }

    public void transition(Status targetStatus, UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        AccommodationValueRules.requireVersion(getVersion(), expectedVersion, "Accommodation rate");
        if (actorUserId == null || actorUserId.equals(preparedByUserId)) {
            throw new IllegalArgumentException("A different authorised operator must approve the accommodation rate.");
        }
        boolean allowed = status == Status.DRAFT && targetStatus == Status.ACTIVE
                || status == Status.ACTIVE && targetStatus == Status.RETIRED;
        if (!allowed) throw new IllegalStateException("Accommodation rate cannot move from " + status + " to " + targetStatus + ".");
        if (targetStatus == Status.ACTIVE && ratingStatus != RatingStatus.RATED) {
            throw new IllegalStateException("An unrated foreign-currency rate cannot be activated.");
        }
        approvedByUserId = actorUserId;
        approvedAt = occurredAt;
        approvalReason = AccommodationValueRules.required(reason, "Approval reason");
        status = targetStatus;
    }

    public AccommodationApplicationPeriod getApplicationPeriod() { return applicationPeriod; }
    public AccommodationRoomType getRoomType() { return roomType; }
    public int getRateVersion() { return rateVersion; }
    public UUID getFinanceFeeCatalogueId() { return financeFeeCatalogueId; }
    public String getTransactionCurrencyCode() { return transactionCurrencyCode; }
    public BigDecimal getIndicativeTransactionAmount() { return indicativeTransactionAmount; }
    public String getBaseCurrencyCode() { return baseCurrencyCode; }
    public UUID getExchangeRateId() { return exchangeRateId; }
    public BigDecimal getIndicativeBaseAmount() { return indicativeBaseAmount; }
    public RatingStatus getRatingStatus() { return ratingStatus; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveUntil() { return effectiveUntil; }
    public Status getStatus() { return status; }
    public UUID getPreparedByUserId() { return preparedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReason() { return approvalReason; }
}
