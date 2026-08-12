package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.billing.domain.model.FinanceInvoice;

/** Controlled receivable correction requiring independent posting. @author Tinashe K */
@Audited @Entity @Table(name="finance_credit_notes") @SQLRestriction("deleted_at IS NULL")
public class FinanceCreditNote extends AuditableEntity {
    public enum Status { DRAFT, POSTED }
    @Column(name="credit_note_number",nullable=false,length=40) private String creditNoteNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id") private FinanceInvoice invoice;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode;
    @Column(name="base_amount",nullable=false,precision=16,scale=2) private BigDecimal baseAmount;
    @Column(name="credit_note_date",nullable=false) private LocalDate creditNoteDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId;
    @Column(name="prepared_at",nullable=false) private Instant preparedAt;
    @Column(name="preparation_reason",nullable=false,length=1000) private String preparationReason;
    @Column(name="posted_by_user_id") private UUID postedByUserId;
    @Column(name="posted_at") private Instant postedAt;
    @Column(name="posting_reason",length=1000) private String postingReason;
    protected FinanceCreditNote() {}
    public FinanceCreditNote(String number,FinanceInvoice invoice,BigDecimal transactionAmount,BigDecimal baseAmount,LocalDate date,UUID actor,Instant at,String reason){creditNoteNumber=required(number,"Credit-note number");this.invoice=Objects.requireNonNull(invoice);transactionCurrencyCode=invoice.getTransactionCurrencyCode();this.transactionAmount=positive(transactionAmount,"Credit-note transaction amount");baseCurrencyCode="USD";this.baseAmount=positive(baseAmount,"Credit-note USD amount");creditNoteDate=Objects.requireNonNull(date);status=Status.DRAFT;preparedByUserId=Objects.requireNonNull(actor);preparedAt=Objects.requireNonNull(at);preparationReason=required(reason,"Credit-note preparation reason");}
    public void post(UUID actor,Instant at,String reason,long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Credit note changed since it was loaded. Refresh and try again.");if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft credit note can be posted.");if(preparedByUserId.equals(actor))throw new IllegalStateException("Credit-note posting requires a different Finance operator.");postedByUserId=Objects.requireNonNull(actor);postedAt=Objects.requireNonNull(at);postingReason=required(reason,"Credit-note posting reason");status=Status.POSTED;}
    private static BigDecimal positive(BigDecimal value,String label){if(value==null||value.signum()<=0)throw new IllegalArgumentException(label+" must be greater than zero.");return value;}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public String getCreditNoteNumber(){return creditNoteNumber;} public FinanceInvoice getInvoice(){return invoice;} public String getTransactionCurrencyCode(){return transactionCurrencyCode;} public BigDecimal getTransactionAmount(){return transactionAmount;} public String getBaseCurrencyCode(){return baseCurrencyCode;} public BigDecimal getBaseAmount(){return baseAmount;} public LocalDate getCreditNoteDate(){return creditNoteDate;} public Status getStatus(){return status;} public UUID getPreparedByUserId(){return preparedByUserId;} public Instant getPreparedAt(){return preparedAt;} public UUID getPostedByUserId(){return postedByUserId;} public Instant getPostedAt(){return postedAt;}
}
