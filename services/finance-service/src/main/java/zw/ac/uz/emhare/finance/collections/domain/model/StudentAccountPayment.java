package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

/** Immutable provider payment evidence with separately controlled rating and reconciliation. @author Tinashe K */
@Audited @Entity @Table(name="student_account_payments") @SQLRestriction("deleted_at IS NULL")
public class StudentAccountPayment extends AuditableEntity {
    public enum RatingStatus { RATED, UNRATED }
    public enum ReconciliationStatus { PENDING, RECONCILED, REJECTED }
    public enum PaymentChannel { CASH, BANK_TRANSFER, CARD, MOBILE_MONEY, ONLINE, OTHER }

    @Column(name="payment_number",nullable=false,length=40) private String paymentNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_finance_account_id") private StudentFinanceAccount studentFinanceAccount;
    @Column(name="payer_name",nullable=false,length=200) private String payerName;
    @Column(name="provider_code",nullable=false,length=50) private String providerCode;
    @Column(name="provider_transaction_reference",nullable=false,length=160) private String providerTransactionReference;
    @Enumerated(EnumType.STRING) @Column(name="payment_channel",nullable=false,length=40) private PaymentChannel paymentChannel;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exchange_rate_id") private ExchangeRate exchangeRate;
    @Column(name="base_amount",precision=16,scale=2) private BigDecimal baseAmount;
    @Enumerated(EnumType.STRING) @Column(name="rating_status",nullable=false,length=20) private RatingStatus ratingStatus;
    @Column(name="rating_applied_by_user_id") private UUID ratingAppliedByUserId;
    @Column(name="rating_applied_at") private Instant ratingAppliedAt;
    @Column(name="paid_at",nullable=false) private Instant paidAt;
    @Column(name="provider_event_fingerprint",nullable=false,length=128) private String providerEventFingerprint;
    @Enumerated(EnumType.STRING) @Column(name="reconciliation_status",nullable=false,length=30) private ReconciliationStatus reconciliationStatus;
    @Column(name="captured_by_user_id",nullable=false) private UUID capturedByUserId;
    @Column(name="captured_at",nullable=false) private Instant capturedAt;
    @Column(name="reconciled_by_user_id") private UUID reconciledByUserId;
    @Column(name="reconciled_at") private Instant reconciledAt;
    @Column(name="reconciliation_reason",length=1000) private String reconciliationReason;
    @Column(name="rejected_by_user_id") private UUID rejectedByUserId;
    @Column(name="rejected_at") private Instant rejectedAt;
    @Column(name="rejection_reason",length=1000) private String rejectionReason;

    protected StudentAccountPayment() {}

    public StudentAccountPayment(String paymentNumber,StudentFinanceAccount account,String payerName,String providerCode,
            String providerTransactionReference,PaymentChannel paymentChannel,String transactionCurrencyCode,
            BigDecimal transactionAmount,ExchangeRate exchangeRate,BigDecimal baseAmount,RatingStatus ratingStatus,
            Instant paidAt,String providerEventFingerprint,UUID capturedByUserId,Instant capturedAt) {
        this.paymentNumber=required(paymentNumber,"Payment number");this.studentFinanceAccount=account;
        this.payerName=required(payerName,"Payer name");this.providerCode=required(providerCode,"Provider code").toUpperCase(Locale.ROOT);
        this.providerTransactionReference=required(providerTransactionReference,"Provider transaction reference");
        this.paymentChannel=Objects.requireNonNull(paymentChannel,"Payment channel is required.");
        this.transactionCurrencyCode=ExchangeRate.normalizeCurrencyCode(transactionCurrencyCode);
        if(transactionAmount==null||transactionAmount.signum()<=0)throw new IllegalArgumentException("Payment amount must be greater than zero.");
        this.transactionAmount=transactionAmount;this.baseCurrencyCode="USD";this.exchangeRate=exchangeRate;this.baseAmount=baseAmount;
        this.ratingStatus=Objects.requireNonNull(ratingStatus,"Payment rating status is required.");
        if(ratingStatus==RatingStatus.RATED){ratingAppliedByUserId=Objects.requireNonNull(capturedByUserId);ratingAppliedAt=Objects.requireNonNull(capturedAt);}
        validateRating();this.paidAt=Objects.requireNonNull(paidAt,"Paid timestamp is required.");
        this.providerEventFingerprint=required(providerEventFingerprint,"Provider event fingerprint");
        this.reconciliationStatus=ReconciliationStatus.PENDING;
        this.capturedByUserId=Objects.requireNonNull(capturedByUserId,"Capture actor is required.");
        this.capturedAt=Objects.requireNonNull(capturedAt,"Capture timestamp is required.");
    }

    public void applyRate(ExchangeRate rate,BigDecimal convertedBaseAmount,UUID actor,Instant at,long expectedVersion){requireVersion(expectedVersion);if(reconciliationStatus!=ReconciliationStatus.PENDING||ratingStatus!=RatingStatus.UNRATED)throw new IllegalStateException("Only a pending unrated payment can receive an exchange rate.");exchangeRate=Objects.requireNonNull(rate);baseAmount=Objects.requireNonNull(convertedBaseAmount);ratingAppliedByUserId=Objects.requireNonNull(actor);ratingAppliedAt=Objects.requireNonNull(at);ratingStatus=RatingStatus.RATED;validateRating();}
    public void reconcile(UUID actor,Instant at,String reason,long expectedVersion){requireVersion(expectedVersion);if(reconciliationStatus!=ReconciliationStatus.PENDING)throw new IllegalStateException("Only a pending payment can be reconciled.");if(ratingStatus!=RatingStatus.RATED)throw new IllegalStateException("An unrated payment cannot be reconciled.");if(capturedByUserId.equals(actor))throw new IllegalStateException("Payment reconciliation requires a different Finance operator.");reconciledByUserId=Objects.requireNonNull(actor);reconciledAt=Objects.requireNonNull(at);reconciliationReason=required(reason,"Reconciliation reason");reconciliationStatus=ReconciliationStatus.RECONCILED;}
    public void reject(UUID actor,Instant at,String reason,long expectedVersion){requireVersion(expectedVersion);if(reconciliationStatus!=ReconciliationStatus.PENDING)throw new IllegalStateException("Only a pending payment can be rejected.");if(capturedByUserId.equals(actor))throw new IllegalStateException("Payment rejection requires a different Finance operator.");rejectedByUserId=Objects.requireNonNull(actor);rejectedAt=Objects.requireNonNull(at);rejectionReason=required(reason,"Rejection reason");reconciliationStatus=ReconciliationStatus.REJECTED;}
    public boolean matches(StudentFinanceAccount account,String providerFingerprint,BigDecimal amount,String currency,Instant paymentTime){return Objects.equals(studentFinanceAccount==null?null:studentFinanceAccount.getId(),account==null?null:account.getId())&&providerEventFingerprint.equals(providerFingerprint.trim())&&transactionAmount.compareTo(amount)==0&&transactionCurrencyCode.equals(currency)&&paidAt.equals(paymentTime);}
    private void validateRating(){if("USD".equals(transactionCurrencyCode)){if(exchangeRate!=null||baseAmount==null||baseAmount.compareTo(transactionAmount)!=0||ratingStatus!=RatingStatus.RATED||ratingAppliedByUserId==null||ratingAppliedAt==null)throw new IllegalArgumentException("USD payments must be rated directly at capture.");}else if(ratingStatus==RatingStatus.UNRATED){if(exchangeRate!=null||baseAmount!=null||ratingAppliedByUserId!=null||ratingAppliedAt!=null)throw new IllegalArgumentException("Unrated foreign-currency payments cannot contain rating evidence.");}else if(exchangeRate==null||baseAmount==null||baseAmount.signum()<=0||ratingAppliedByUserId==null||ratingAppliedAt==null)throw new IllegalArgumentException("Rated foreign-currency payments require an effective rate, USD amount, and rating actor.");}
    private void requireVersion(long expected){if(getVersion()!=expected)throw new IllegalStateException("Payment changed since it was loaded. Refresh and try again.");}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}

    public String getPaymentNumber(){return paymentNumber;} public StudentFinanceAccount getStudentFinanceAccount(){return studentFinanceAccount;} public String getPayerName(){return payerName;} public String getProviderCode(){return providerCode;} public String getProviderTransactionReference(){return providerTransactionReference;} public PaymentChannel getPaymentChannel(){return paymentChannel;} public String getTransactionCurrencyCode(){return transactionCurrencyCode;} public BigDecimal getTransactionAmount(){return transactionAmount;} public String getBaseCurrencyCode(){return baseCurrencyCode;} public ExchangeRate getExchangeRate(){return exchangeRate;} public BigDecimal getBaseAmount(){return baseAmount;} public RatingStatus getRatingStatus(){return ratingStatus;} public Instant getPaidAt(){return paidAt;} public String getProviderEventFingerprint(){return providerEventFingerprint;} public ReconciliationStatus getReconciliationStatus(){return reconciliationStatus;} public UUID getCapturedByUserId(){return capturedByUserId;} public Instant getCapturedAt(){return capturedAt;} public UUID getReconciledByUserId(){return reconciledByUserId;} public Instant getReconciledAt(){return reconciledAt;}
}
