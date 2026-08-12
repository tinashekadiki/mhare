package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.billing.domain.model.FinanceInvoice;

/** Append-only settlement link retaining both payment and invoice USD bases. @author Tinashe K */
@Audited @Entity @Table(name="student_payment_allocations") @SQLRestriction("deleted_at IS NULL")
public class StudentPaymentAllocation extends AuditableEntity {
    @Column(name="allocation_number",nullable=false,length=40) private String allocationNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id") private StudentAccountPayment payment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id") private FinanceInvoice invoice;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @Column(name="payment_base_amount",nullable=false,precision=16,scale=2) private BigDecimal paymentBaseAmount;
    @Column(name="invoice_base_amount",nullable=false,precision=16,scale=2) private BigDecimal invoiceBaseAmount;
    @Column(name="realised_exchange_difference",nullable=false,precision=16,scale=2) private BigDecimal realisedExchangeDifference;
    @Column(name="allocated_by_user_id",nullable=false) private UUID allocatedByUserId;
    @Column(name="allocated_at",nullable=false) private Instant allocatedAt;
    @Column(name="allocation_reason",nullable=false,length=1000) private String allocationReason;
    protected StudentPaymentAllocation() {}
    public StudentPaymentAllocation(String number,StudentAccountPayment payment,FinanceInvoice invoice,BigDecimal transactionAmount,BigDecimal paymentBaseAmount,BigDecimal invoiceBaseAmount,UUID actor,Instant at,String reason){allocationNumber=required(number,"Allocation number");this.payment=Objects.requireNonNull(payment);this.invoice=Objects.requireNonNull(invoice);transactionCurrencyCode=payment.getTransactionCurrencyCode();if(transactionAmount==null||transactionAmount.signum()<=0)throw new IllegalArgumentException("Allocation amount must be greater than zero.");this.transactionAmount=transactionAmount;baseCurrencyCode="USD";this.paymentBaseAmount=positive(paymentBaseAmount,"Payment-basis USD amount");this.invoiceBaseAmount=positive(invoiceBaseAmount,"Invoice-basis USD amount");realisedExchangeDifference=this.paymentBaseAmount.subtract(this.invoiceBaseAmount);allocatedByUserId=Objects.requireNonNull(actor);allocatedAt=Objects.requireNonNull(at);allocationReason=required(reason,"Allocation reason");}
    private static BigDecimal positive(BigDecimal value,String label){if(value==null||value.signum()<=0)throw new IllegalArgumentException(label+" must be greater than zero.");return value;}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public String getAllocationNumber(){return allocationNumber;} public StudentAccountPayment getPayment(){return payment;} public FinanceInvoice getInvoice(){return invoice;} public String getTransactionCurrencyCode(){return transactionCurrencyCode;} public BigDecimal getTransactionAmount(){return transactionAmount;} public BigDecimal getPaymentBaseAmount(){return paymentBaseAmount;} public BigDecimal getInvoiceBaseAmount(){return invoiceBaseAmount;} public BigDecimal getRealisedExchangeDifference(){return realisedExchangeDifference;} public UUID getAllocatedByUserId(){return allocatedByUserId;} public Instant getAllocatedAt(){return allocatedAt;}
}
