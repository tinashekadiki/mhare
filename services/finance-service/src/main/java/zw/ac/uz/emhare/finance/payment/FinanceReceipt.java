package zw.ac.uz.emhare.finance.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(
        name = "finance_receipts",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_finance_receipts_payment", columnNames = "application_payment_id"),
            @UniqueConstraint(name = "uk_finance_receipts_number", columnNames = "receipt_number")
        })
public class FinanceReceipt extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_payment_id", nullable = false)
    private ApplicationPayment applicationPayment;

    @Column(name = "receipt_number", nullable = false, length = 80)
    private String receiptNumber;

    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceReceiptStatus status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    protected FinanceReceipt() {
    }

    public FinanceReceipt(ApplicationPayment applicationPayment, String receiptNumber) {
        this.applicationPayment = applicationPayment;
        this.receiptNumber = receiptNumber;
        this.status = FinanceReceiptStatus.PENDING_GENERATION;
    }
}
