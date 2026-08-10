package zw.ac.uz.emhare.finance.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.student.StudentFinanceAccount;

/** Immutable posted receivable document assembled only from approved billing events. @author Tinashe K */
@Audited @Entity @Table(name="finance_invoices") @SQLRestriction("deleted_at IS NULL")
public class FinanceInvoice extends AuditableEntity {
    @Column(name="invoice_number",nullable=false,length=40) private String invoiceNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_finance_account_id") private StudentFinanceAccount studentFinanceAccount;
    @Column(name="student_id",nullable=false) private UUID studentId; @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode; @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @Column(name="gross_transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal grossTransactionAmount; @Column(name="transaction_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionDiscountAmount; @Column(name="net_transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal netTransactionAmount; @Column(name="gross_base_amount",nullable=false,precision=16,scale=2) private BigDecimal grossBaseAmount; @Column(name="base_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal baseDiscountAmount; @Column(name="net_base_amount",nullable=false,precision=16,scale=2) private BigDecimal netBaseAmount;
    @Column(name="invoice_date",nullable=false) private LocalDate invoiceDate; @Column(name="due_date",nullable=false) private LocalDate dueDate;
    @Column(nullable=false,length=20) private String status; @Column(name="posted_by_user_id",nullable=false) private UUID postedByUserId; @Column(name="posted_at",nullable=false) private Instant postedAt; @Column(name="posting_reason",nullable=false,length=1000) private String postingReason;
    protected FinanceInvoice() {}
    public FinanceInvoice(String number,StudentFinanceAccount account,String transactionCurrency,BigDecimal grossTransactionTotal,
            BigDecimal transactionDiscountTotal,BigDecimal netTransactionTotal,BigDecimal grossBaseTotal,
            BigDecimal baseDiscountTotal,BigDecimal netBaseTotal,LocalDate invoiceDate,LocalDate dueDate,UUID poster,
            Instant postedAt,String reason){invoiceNumber=required(number,"Invoice number");studentFinanceAccount=Objects.requireNonNull(account);studentId=account.getStudentId();studentNumber=account.getStudentNumber();transactionCurrencyCode=required(transactionCurrency,"Transaction currency");baseCurrencyCode="USD";if(grossTransactionTotal==null||grossTransactionTotal.signum()<=0||netTransactionTotal==null||netTransactionTotal.signum()<=0||grossBaseTotal==null||grossBaseTotal.signum()<=0||netBaseTotal==null||netBaseTotal.signum()<=0)throw new IllegalArgumentException("Invoice totals must be greater than zero.");grossTransactionAmount=grossTransactionTotal;transactionDiscountAmount=transactionDiscountTotal;netTransactionAmount=netTransactionTotal;grossBaseAmount=grossBaseTotal;baseDiscountAmount=baseDiscountTotal;netBaseAmount=netBaseTotal;this.invoiceDate=Objects.requireNonNull(invoiceDate);this.dueDate=Objects.requireNonNull(dueDate);if(dueDate.isBefore(invoiceDate))throw new IllegalArgumentException("Invoice due date cannot be before its invoice date.");postedByUserId=Objects.requireNonNull(poster);this.postedAt=Objects.requireNonNull(postedAt);postingReason=required(reason,"Invoice posting reason");status="POSTED";}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public String getInvoiceNumber(){return invoiceNumber;} public StudentFinanceAccount getStudentFinanceAccount(){return studentFinanceAccount;} public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;}
    public String getTransactionCurrencyCode(){return transactionCurrencyCode;} public String getBaseCurrencyCode(){return baseCurrencyCode;} public BigDecimal getGrossTransactionAmount(){return grossTransactionAmount;} public BigDecimal getTransactionDiscountAmount(){return transactionDiscountAmount;} public BigDecimal getNetTransactionAmount(){return netTransactionAmount;} public BigDecimal getGrossBaseAmount(){return grossBaseAmount;} public BigDecimal getBaseDiscountAmount(){return baseDiscountAmount;} public BigDecimal getNetBaseAmount(){return netBaseAmount;}
    public LocalDate getInvoiceDate(){return invoiceDate;} public LocalDate getDueDate(){return dueDate;} public String getStatus(){return status;} public UUID getPostedByUserId(){return postedByUserId;} public Instant getPostedAt(){return postedAt;}
}
