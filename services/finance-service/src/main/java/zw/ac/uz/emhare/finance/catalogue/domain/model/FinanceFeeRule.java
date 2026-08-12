package zw.ac.uz.emhare.finance.catalogue.domain.model;

import zw.ac.uz.emhare.finance.catalogue.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;

/** Effective, rated, versioned price for a governed institutional fee. @author Tinashe K */
@Audited @Entity @Table(name="finance_fee_rules") @SQLRestriction("deleted_at IS NULL")
public class FinanceFeeRule extends AuditableEntity {
    public enum RatingStatus { RATED,UNRATED } public enum Status { DRAFT,PENDING_RATE,APPROVED,RETIRED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_catalogue_id") private FinanceFeeCatalogue feeCatalogue;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="fee_structure_id") private FinanceFeeStructure feeStructure;
    @Column(name="structure_line_number") private Integer structureLineNumber;
    @Column(name="structure_line_description",length=500) private String structureLineDescription;
    @Column(name="rule_version",nullable=false) private int ruleVersion;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_amount",nullable=false,precision=14,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exchange_rate_id") private ExchangeRate exchangeRate;
    @Column(name="base_amount",precision=14,scale=2) private BigDecimal baseAmount;
    @Enumerated(EnumType.STRING) @Column(name="rating_status",nullable=false,length=20) private RatingStatus ratingStatus;
    @Column(name="effective_from",nullable=false) private Instant effectiveFrom; @Column(name="effective_until") private Instant effectiveUntil;
    @Column(name="scope_signature") private String scopeSignature; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="approved_by_user_id") private UUID approvedByUserId; @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approval_reason",length=1000) private String approvalReason;
    @Column(name="retired_by_user_id") private UUID retiredByUserId; @Column(name="retired_at") private Instant retiredAt;
    @Column(name="retirement_reason",length=1000) private String retirementReason;
    protected FinanceFeeRule() {}
    public FinanceFeeRule(FinanceFeeCatalogue catalogue,int version,String currency,BigDecimal amount,ExchangeRate exchangeRate,
            BigDecimal baseAmount,Instant effectiveFrom,Instant effectiveUntil,UUID preparer) {
        if(version<1)throw new IllegalArgumentException("Fee rule version must be positive.");if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("Fee amount must be greater than zero.");
        if(effectiveFrom==null||effectiveUntil!=null&&!effectiveUntil.isAfter(effectiveFrom))throw new IllegalArgumentException("Fee rule effective dates are invalid.");
        feeCatalogue=catalogue;ruleVersion=version;transactionCurrencyCode=ExchangeRate.normalizeCurrencyCode(currency);transactionAmount=amount;
        baseCurrencyCode="USD";this.exchangeRate=exchangeRate;this.baseAmount=baseAmount;ratingStatus=baseAmount==null?RatingStatus.UNRATED:RatingStatus.RATED;
        this.effectiveFrom=effectiveFrom;this.effectiveUntil=effectiveUntil;preparedByUserId=preparer;status=ratingStatus==RatingStatus.RATED?Status.DRAFT:Status.PENDING_RATE;
    }
    public FinanceFeeRule(FinanceFeeCatalogue catalogue,FinanceFeeStructure structure,int lineNumber,String lineDescription,
            int version,String currency,BigDecimal amount,ExchangeRate exchangeRate,BigDecimal baseAmount,
            Instant effectiveFrom,Instant effectiveUntil,UUID preparer) {
        this(catalogue,version,currency,amount,exchangeRate,baseAmount,effectiveFrom,effectiveUntil,preparer);
        if(structure==null)throw new IllegalArgumentException("Fee structure is required.");
        if(lineNumber<1)throw new IllegalArgumentException("Fee structure line number must be positive.");
        feeStructure=structure;structureLineNumber=lineNumber;
        structureLineDescription=FinanceFeeCatalogue.required(lineDescription,"Fee structure line description");
    }
    public void applyRate(ExchangeRate rate,BigDecimal ratedBaseAmount,long expectedVersion){version(expectedVersion);if(status!=Status.PENDING_RATE&&status!=Status.DRAFT)throw new IllegalStateException("Only an unapproved fee rule can be rated.");if(rate==null||ratedBaseAmount==null||ratedBaseAmount.signum()<=0)throw new IllegalArgumentException("Effective exchange-rate evidence is required.");exchangeRate=rate;baseAmount=ratedBaseAmount;ratingStatus=RatingStatus.RATED;status=Status.DRAFT;}
    public void approve(UUID actor,Instant now,String reason,String canonicalScope,long expectedVersion){version(expectedVersion);if(status!=Status.DRAFT||ratingStatus!=RatingStatus.RATED)throw new IllegalStateException("Only a fully rated draft fee rule can be approved.");if(feeCatalogue.getStatus()!=FinanceFeeCatalogue.Status.ACTIVE)throw new IllegalStateException("Fee catalogue must be active before pricing approval.");FinanceFeeCatalogue.distinct(actor,preparedByUserId,"Fee rule approval requires an independent finance operator.");scopeSignature=FinanceFeeCatalogue.required(canonicalScope,"Fee rule scope");approvedByUserId=actor;approvedAt=now;approvalReason=FinanceFeeCatalogue.required(reason,"Approval reason");status=Status.APPROVED;}
    public void retire(UUID actor,Instant now,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.APPROVED)throw new IllegalStateException("Only an approved fee rule can be retired.");retiredByUserId=actor;retiredAt=now;retirementReason=FinanceFeeCatalogue.required(reason,"Retirement reason");status=Status.RETIRED;}
    private void version(long expected){if(getVersion()!=expected)throw new IllegalStateException("Fee rule was changed by another user. Refresh before retrying.");}
    public FinanceFeeCatalogue getFeeCatalogue(){return feeCatalogue;} public int getRuleVersion(){return ruleVersion;}
    public FinanceFeeStructure getFeeStructure(){return feeStructure;} public Integer getStructureLineNumber(){return structureLineNumber;}
    public String getStructureLineDescription(){return structureLineDescription;}
    public String getTransactionCurrencyCode(){return transactionCurrencyCode;} public BigDecimal getTransactionAmount(){return transactionAmount;}
    public String getBaseCurrencyCode(){return baseCurrencyCode;} public ExchangeRate getExchangeRate(){return exchangeRate;}
    public BigDecimal getBaseAmount(){return baseAmount;} public RatingStatus getRatingStatus(){return ratingStatus;}
    public Instant getEffectiveFrom(){return effectiveFrom;} public Instant getEffectiveUntil(){return effectiveUntil;}
    public String getScopeSignature(){return scopeSignature;} public Status getStatus(){return status;}
    public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;}
}
