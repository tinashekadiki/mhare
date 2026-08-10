package zw.ac.uz.emhare.finance.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FinanceReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;

    public FinanceReferenceGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextPaymentReference() {
        return "EMH-PAY-%010d".formatted(nextSequenceValue("application_payment_reference_sequence"));
    }

    public String nextReceiptNumber() {
        return "EMH-RCT-%010d".formatted(nextSequenceValue("finance_receipt_sequence"));
    }

    public String nextBillingEventNumber() {
        return "EMH-BLE-%010d".formatted(nextSequenceValue("finance_billing_event_number_sequence"));
    }

    public String nextInvoiceNumber() {
        return "EMH-INV-%010d".formatted(nextSequenceValue("finance_invoice_number_sequence"));
    }

    public String nextStudentPaymentNumber() { return "EMH-SPY-%010d".formatted(nextSequenceValue("student_payment_number_sequence")); }
    public String nextStudentReceiptNumber() { return "EMH-SRC-%010d".formatted(nextSequenceValue("student_payment_receipt_number_sequence")); }
    public String nextAllocationNumber() { return "EMH-ALL-%010d".formatted(nextSequenceValue("student_payment_allocation_number_sequence")); }
    public String nextCreditNoteNumber() { return "EMH-CRN-%010d".formatted(nextSequenceValue("finance_credit_note_number_sequence")); }
    public String nextReversalNumber() { return "EMH-REV-%010d".formatted(nextSequenceValue("finance_reversal_number_sequence")); }

    private long nextSequenceValue(String sequenceName) {
        Long value = jdbcTemplate.queryForObject("select nextval('" + sequenceName + "')", Long.class);
        if (value == null) {
            throw new IllegalStateException("Finance reference sequence did not return a value.");
        }
        return value;
    }
}
