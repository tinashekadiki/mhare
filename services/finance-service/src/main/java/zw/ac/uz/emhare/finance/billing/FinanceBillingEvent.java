package zw.ac.uz.emhare.finance.billing;

import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.AppliedDiscount;
import zw.ac.uz.emhare.finance.payment.ExchangeRate;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccount;

/** One idempotent, independently approved charge occurrence from an authoritative source. @author Tinashe K */
@Audited @Entity @Table(name="finance_billing_events") @SQLRestriction("deleted_at IS NULL")
public class FinanceBillingEvent extends AuditableEntity {
    public enum Status { PENDING_APPROVAL,APPROVED,REJECTED,INVOICED }
    @Column(name="event_number",nullable=false,length=40) private String eventNumber;
    @Column(name="source_service",nullable=false,length=80) private String sourceService;
    @Column(name="source_event_type",nullable=false,length=160) private String sourceEventType;
    @Column(name="source_event_id",nullable=false) private UUID sourceEventId;
    @Column(name="source_aggregate_type",nullable=false,length=80) private String sourceAggregateType;
    @Column(name="source_aggregate_id",nullable=false) private UUID sourceAggregateId;
    @Column(name="source_line_reference",nullable=false,length=160) private String sourceLineReference;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_finance_account_id") private StudentFinanceAccount studentFinanceAccount;
    @Column(name="student_id",nullable=false) private UUID studentId; @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_catalogue_id") private FinanceFeeCatalogue feeCatalogue;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_rule_id") private FinanceFeeRule feeRule;
    @Column(nullable=false,length=500) private String description; @Column(nullable=false,precision=12,scale=4) private BigDecimal quantity;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_unit_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionUnitAmount;
    @Column(name="gross_transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal grossTransactionAmount;
    @Column(name="transaction_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionDiscountAmount;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exchange_rate_id") private ExchangeRate exchangeRate;
    @Column(name="base_unit_amount",nullable=false,precision=16,scale=2) private BigDecimal baseUnitAmount;
    @Column(name="gross_base_amount",nullable=false,precision=16,scale=2) private BigDecimal grossBaseAmount;
    @Column(name="base_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal baseDiscountAmount;
    @Column(name="base_amount",nullable=false,precision=16,scale=2) private BigDecimal baseAmount;
    @Column(name="discount_rule_id") private UUID discountRuleId;
    @Column(name="discount_rule_code",length=50) private String discountRuleCode;
    @Column(name="discount_percentage",precision=7,scale=4) private BigDecimal discountPercentage;
    @Column(name="effective_at",nullable=false) private Instant effectiveAt; @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId; @Column(name="submitted_at",nullable=false) private Instant submittedAt;
    @Column(name="approved_by_user_id") private UUID approvedByUserId; @Column(name="approved_at") private Instant approvedAt; @Column(name="approval_reason",length=1000) private String approvalReason;
    @Column(name="rejected_by_user_id") private UUID rejectedByUserId; @Column(name="rejected_at") private Instant rejectedAt; @Column(name="rejection_reason",length=1000) private String rejectionReason;
    @Column(name="invoiced_at") private Instant invoicedAt;
    protected FinanceBillingEvent() {}
    public FinanceBillingEvent(String eventNumber,String sourceService,String sourceEventType,UUID sourceEventId,
            String sourceAggregateType,UUID sourceAggregateId,String sourceLineReference,StudentFinanceAccount account,
            FinanceFeeCatalogue catalogue,FinanceFeeRule rule,String description,BigDecimal quantity,Instant effectiveAt,
            UUID preparer,Instant submittedAt) {
        this(eventNumber,sourceService,sourceEventType,sourceEventId,sourceAggregateType,sourceAggregateId,
                sourceLineReference,account,catalogue,rule,description,quantity,effectiveAt,null,preparer,submittedAt);
    }
    public FinanceBillingEvent(String eventNumber,String sourceService,String sourceEventType,UUID sourceEventId,
            String sourceAggregateType,UUID sourceAggregateId,String sourceLineReference,StudentFinanceAccount account,
            FinanceFeeCatalogue catalogue,FinanceFeeRule rule,String description,BigDecimal quantity,Instant effectiveAt,
            AppliedDiscount discount,UUID preparer,Instant submittedAt) {
        this.eventNumber=required(eventNumber,"Billing event number");this.sourceService=required(sourceService,"Source service");
        this.sourceEventType=required(sourceEventType,"Source event type");this.sourceEventId=Objects.requireNonNull(sourceEventId,"Source event identifier is required.");
        this.sourceAggregateType=required(sourceAggregateType,"Source aggregate type");this.sourceAggregateId=Objects.requireNonNull(sourceAggregateId,"Source aggregate identifier is required.");
        this.sourceLineReference=required(sourceLineReference,"Source line reference");studentFinanceAccount=Objects.requireNonNull(account,"Student finance account is required.");
        if(!"ACTIVE".equals(account.getStatus()))throw new IllegalStateException("Billing requires an active student finance account.");
        studentId=account.getStudentId();studentNumber=account.getStudentNumber();feeCatalogue=Objects.requireNonNull(catalogue,"Fee definition is required.");feeRule=Objects.requireNonNull(rule,"Fee rule is required.");
        this.description=required(description,"Billing description");if(quantity==null||quantity.signum()<=0)throw new IllegalArgumentException("Billing quantity must be greater than zero.");
        this.quantity=quantity.setScale(4,RoundingMode.UNNECESSARY);transactionCurrencyCode=rule.getTransactionCurrencyCode();transactionUnitAmount=rule.getTransactionAmount();
        grossTransactionAmount=transactionUnitAmount.multiply(this.quantity).setScale(2,RoundingMode.HALF_UP);baseCurrencyCode=rule.getBaseCurrencyCode();exchangeRate=rule.getExchangeRate();
        if(rule.getStatus()!=FinanceFeeRule.Status.APPROVED||rule.getRatingStatus()!=FinanceFeeRule.RatingStatus.RATED||rule.getBaseAmount()==null)throw new IllegalStateException("Billing requires an approved rated fee rule.");
        baseUnitAmount=rule.getBaseAmount();grossBaseAmount=baseUnitAmount.multiply(this.quantity).setScale(2,RoundingMode.HALF_UP);
        if(discount==null){transactionDiscountAmount=BigDecimal.ZERO.setScale(2);baseDiscountAmount=BigDecimal.ZERO.setScale(2);}
        else{discountRuleId=discount.id();discountRuleCode=discount.code();discountPercentage=discount.percentage();transactionDiscountAmount=percentage(grossTransactionAmount,discountPercentage);baseDiscountAmount=percentage(grossBaseAmount,discountPercentage);}
        transactionAmount=grossTransactionAmount.subtract(transactionDiscountAmount);baseAmount=grossBaseAmount.subtract(baseDiscountAmount);
        this.effectiveAt=Objects.requireNonNull(effectiveAt,"Billing effective time is required.");
        preparedByUserId=Objects.requireNonNull(preparer,"Billing preparer is required.");this.submittedAt=Objects.requireNonNull(submittedAt,"Billing submission time is required.");status=Status.PENDING_APPROVAL;
    }
    public void decide(boolean approve,UUID actor,Instant decidedAt,String reason,long expectedVersion){version(expectedVersion);if(status!=Status.PENDING_APPROVAL)throw new IllegalStateException("Only a pending billing event can be decided.");if(actor==null||actor.equals(preparedByUserId))throw new IllegalStateException("Billing-event decision requires an independent Finance operator.");String evidence=required(reason,approve?"Approval reason":"Rejection reason");if(approve){approvedByUserId=actor;approvedAt=decidedAt;approvalReason=evidence;status=Status.APPROVED;}else{rejectedByUserId=actor;rejectedAt=decidedAt;rejectionReason=evidence;status=Status.REJECTED;}}
    public void markInvoiced(Instant time){if(status!=Status.APPROVED)throw new IllegalStateException("Only an approved billing event can be invoiced.");invoicedAt=time;status=Status.INVOICED;}
    private void version(long expected){if(getVersion()!=expected)throw new IllegalStateException("Billing event was changed by another user. Refresh before retrying.");}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    private static BigDecimal percentage(BigDecimal amount,BigDecimal percentage){return amount.multiply(percentage).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}
    public String getEventNumber(){return eventNumber;} public String getSourceService(){return sourceService;} public String getSourceEventType(){return sourceEventType;}
    public UUID getSourceEventId(){return sourceEventId;} public String getSourceAggregateType(){return sourceAggregateType;} public UUID getSourceAggregateId(){return sourceAggregateId;}
    public String getSourceLineReference(){return sourceLineReference;} public StudentFinanceAccount getStudentFinanceAccount(){return studentFinanceAccount;}
    public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;} public FinanceFeeCatalogue getFeeCatalogue(){return feeCatalogue;} public FinanceFeeRule getFeeRule(){return feeRule;}
    public String getDescription(){return description;} public BigDecimal getQuantity(){return quantity;} public String getTransactionCurrencyCode(){return transactionCurrencyCode;}
    public BigDecimal getTransactionUnitAmount(){return transactionUnitAmount;} public BigDecimal getGrossTransactionAmount(){return grossTransactionAmount;} public BigDecimal getTransactionDiscountAmount(){return transactionDiscountAmount;} public BigDecimal getTransactionAmount(){return transactionAmount;} public String getBaseCurrencyCode(){return baseCurrencyCode;}
    public ExchangeRate getExchangeRate(){return exchangeRate;} public BigDecimal getBaseUnitAmount(){return baseUnitAmount;} public BigDecimal getGrossBaseAmount(){return grossBaseAmount;} public BigDecimal getBaseDiscountAmount(){return baseDiscountAmount;} public BigDecimal getBaseAmount(){return baseAmount;}
    public UUID getDiscountRuleId(){return discountRuleId;} public String getDiscountRuleCode(){return discountRuleCode;} public BigDecimal getDiscountPercentage(){return discountPercentage;}
    public Instant getEffectiveAt(){return effectiveAt;} public Status getStatus(){return status;} public UUID getPreparedByUserId(){return preparedByUserId;} public Instant getSubmittedAt(){return submittedAt;}
    public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;} public Instant getInvoicedAt(){return invoicedAt;}
}
