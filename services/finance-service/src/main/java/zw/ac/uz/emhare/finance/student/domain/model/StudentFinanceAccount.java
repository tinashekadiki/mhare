package zw.ac.uz.emhare.finance.student.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.student.*;

/**
 * @author Tinashe K
 */
@Audited
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "student_finance_accounts")
public class StudentFinanceAccount extends AuditableEntity {
  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(name = "student_number", nullable = false, length = 40)
  private String studentNumber;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "source_offer_id", nullable = false)
  private UUID sourceOfferId;

  @Column(name = "primary_email", nullable = false, length = 200)
  private String primaryEmail;

  @Column(name = "base_currency_code", nullable = false, length = 3)
  private String baseCurrencyCode;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  protected StudentFinanceAccount() {}

  public StudentFinanceAccount(
      StudentFinanceAccountProvisioningRequestedEvent event, Instant openedAt) {
    this.accountNumber = requireRegistrationNumber(event.studentNumber());
    this.studentId = event.studentId();
    this.studentNumber = this.accountNumber;
    this.userId = event.userId();
    this.sourceOfferId = event.sourceOfferId();
    this.primaryEmail = event.primaryEmail();
    this.baseCurrencyCode = "USD";
    this.status = "ACTIVE";
    this.openedAt = openedAt;
  }

  private static String requireRegistrationNumber(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Student registration number is required for the finance account.");
    }
    return value.trim();
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

  public String getPrimaryEmail() {
    return primaryEmail;
  }
}
