package zw.ac.uz.emhare.finance.collections;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.billing.FinanceInvoiceLine;

/** Immutable line-level linkage from a credit note to its original invoice evidence. @author Tinashe K */
@Audited @Entity @Table(name="finance_credit_note_lines") @SQLRestriction("deleted_at IS NULL")
public class FinanceCreditNoteLine extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="credit_note_id") private FinanceCreditNote creditNote;
    @Column(name="line_number",nullable=false) private int lineNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_line_id") private FinanceInvoiceLine invoiceLine;
    @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_amount",nullable=false,precision=16,scale=2) private BigDecimal baseAmount;
    @Column(nullable=false,length=500) private String reason;
    protected FinanceCreditNoteLine() {}
    public FinanceCreditNoteLine(FinanceCreditNote note,int lineNumber,FinanceInvoiceLine invoiceLine,BigDecimal transactionAmount,BigDecimal baseAmount,String reason){creditNote=java.util.Objects.requireNonNull(note);if(lineNumber<1)throw new IllegalArgumentException("Credit-note line number must be positive.");this.lineNumber=lineNumber;this.invoiceLine=java.util.Objects.requireNonNull(invoiceLine);this.transactionAmount=positive(transactionAmount);this.baseAmount=positive(baseAmount);if(reason==null||reason.isBlank())throw new IllegalArgumentException("Credit-note line reason is required.");this.reason=reason.trim();}
    private static BigDecimal positive(BigDecimal value){if(value==null||value.signum()<=0)throw new IllegalArgumentException("Credit-note line amounts must be greater than zero.");return value;}
    public FinanceCreditNote getCreditNote(){return creditNote;} public int getLineNumber(){return lineNumber;} public FinanceInvoiceLine getInvoiceLine(){return invoiceLine;} public BigDecimal getTransactionAmount(){return transactionAmount;} public BigDecimal getBaseAmount(){return baseAmount;} public String getReason(){return reason;}
}
