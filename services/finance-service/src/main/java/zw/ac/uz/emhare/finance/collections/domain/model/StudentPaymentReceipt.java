package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

/** Immutable receipt evidence issued from a reconciled, rated payment. @author Tinashe K */
@Audited @Entity @Table(name="student_payment_receipts") @SQLRestriction("deleted_at IS NULL")
public class StudentPaymentReceipt extends AuditableEntity {
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id") private StudentAccountPayment payment;
    @Column(name="receipt_number",nullable=false,length=40) private String receiptNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_finance_account_id") private StudentFinanceAccount studentFinanceAccount;
    @Column(name="payer_name",nullable=false,length=200) private String payerName;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @Column(name="base_amount",nullable=false,precision=16,scale=2) private BigDecimal baseAmount;
    @Column(name="issued_by_user_id",nullable=false) private UUID issuedByUserId;
    @Column(name="issued_at",nullable=false) private Instant issuedAt;
    protected StudentPaymentReceipt() {}
    public StudentPaymentReceipt(StudentAccountPayment payment,String receiptNumber,StudentFinanceAccount account,UUID actor,Instant at){this.payment=Objects.requireNonNull(payment);this.receiptNumber=required(receiptNumber);studentFinanceAccount=Objects.requireNonNull(account);payerName=payment.getPayerName();transactionCurrencyCode=payment.getTransactionCurrencyCode();transactionAmount=payment.getTransactionAmount();baseCurrencyCode="USD";baseAmount=Objects.requireNonNull(payment.getBaseAmount());issuedByUserId=Objects.requireNonNull(actor);issuedAt=Objects.requireNonNull(at);}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Receipt number is required.");return value.trim();}
    public StudentAccountPayment getPayment(){return payment;} public String getReceiptNumber(){return receiptNumber;} public StudentFinanceAccount getStudentFinanceAccount(){return studentFinanceAccount;} public Instant getIssuedAt(){return issuedAt;}
}
