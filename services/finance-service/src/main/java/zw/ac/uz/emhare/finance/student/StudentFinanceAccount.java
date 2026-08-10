package zw.ac.uz.emhare.finance.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_finance_accounts")
public class StudentFinanceAccount extends AuditableEntity {
    @Column(name = "account_number", nullable = false, length = 50) private String accountNumber;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40) private String studentNumber;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "source_offer_id", nullable = false) private UUID sourceOfferId;
    @Column(name = "primary_email", nullable = false, length = 200) private String primaryEmail;
    @Column(name = "base_currency_code", nullable = false, length = 3) private String baseCurrencyCode;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "closed_at") private Instant closedAt;

    protected StudentFinanceAccount() {
    }

    public StudentFinanceAccount(
            String accountNumber, StudentFinanceAccountProvisioningRequestedEvent event, Instant openedAt) {
        this.accountNumber = accountNumber;
        this.studentId = event.studentId();
        this.studentNumber = event.studentNumber();
        this.userId = event.userId();
        this.sourceOfferId = event.sourceOfferId();
        this.primaryEmail = event.primaryEmail();
        this.baseCurrencyCode = "USD";
        this.status = "ACTIVE";
        this.openedAt = openedAt;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSourceOfferId() {
        return sourceOfferId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getPrimaryEmail() { return primaryEmail; }
}
