package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Finance pricing or audited fee-free evidence captured with an application. @author
 * Tinashe K
 */
@Embeddable
public class ApplicationFeePolicySnapshot {
  public enum PolicyStatus {
    FEE_STRUCTURE,
    FEE_FREE,
    LEGACY_UNSNAPSHOTTED
  }

  @Column(name = "application_fee_policy_status", nullable = false, length = 30)
  private String policyStatus;

  @Column(name = "application_fee_structure_id")
  private UUID feeStructureId;

  @Column(name = "application_fee_structure_code", length = 50)
  private String feeStructureCode;

  @Column(name = "application_fee_structure_name", length = 160)
  private String feeStructureName;

  @Column(name = "application_fee_structure_version")
  private Long feeStructureVersion;

  @Column(name = "application_fee_programme_level_id")
  private UUID programmeLevelId;

  @Column(name = "application_fee_programme_level_code", length = 80)
  private String programmeLevelCode;

  @Column(name = "application_fee_applicant_category_code", length = 80)
  private String applicantCategoryCode;

  @Column(name = "application_fee_amount", precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "application_fee_currency_code", length = 3)
  private String currencyCode;

  @Column(name = "application_fee_effective_at")
  private Instant effectiveAt;

  @Column(name = "application_fee_free_reason", length = 1000)
  private String feeFreeReason;

  @Column(name = "application_fee_policy_decided_by_user_id")
  private UUID feePolicyDecidedByUserId;

  @Column(name = "application_fee_policy_decided_at")
  private Instant feePolicyDecidedAt;

  protected ApplicationFeePolicySnapshot() {}

  public static ApplicationFeePolicySnapshot financeStructure(
      UUID feeStructureId,
      String feeStructureCode,
      String feeStructureName,
      long feeStructureVersion,
      UUID programmeLevelId,
      String programmeLevelCode,
      String applicantCategoryCode,
      BigDecimal amount,
      String currencyCode,
      Instant effectiveAt) {
    if (feeStructureVersion < 0) {
      throw new IllegalArgumentException("Finance fee-structure version cannot be negative.");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException(
          "Resolved application-fee amount must be greater than zero.");
    }
    if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException(
          "Resolved application-fee currency must be a three-letter uppercase code.");
    }
    ApplicationFeePolicySnapshot snapshot = new ApplicationFeePolicySnapshot();
    snapshot.policyStatus = PolicyStatus.FEE_STRUCTURE.name();
    snapshot.feeStructureId =
        Objects.requireNonNull(feeStructureId, "Finance fee-structure ID is required.");
    snapshot.feeStructureCode =
        requiredText(feeStructureCode, "Finance fee-structure code", 50).toUpperCase(Locale.ROOT);
    snapshot.feeStructureName = requiredText(feeStructureName, "Finance fee-structure name", 160);
    snapshot.feeStructureVersion = feeStructureVersion;
    snapshot.programmeLevelId =
        Objects.requireNonNull(programmeLevelId, "Programme level ID is required.");
    snapshot.programmeLevelCode =
        requiredText(programmeLevelCode, "Programme level code", 80).toUpperCase(Locale.ROOT);
    snapshot.applicantCategoryCode =
        requiredText(applicantCategoryCode, "Applicant category", 80).toUpperCase(Locale.ROOT);
    snapshot.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    snapshot.currencyCode = currencyCode;
    snapshot.effectiveAt =
        Objects.requireNonNull(effectiveAt, "Pricing effective time is required.");
    return snapshot;
  }

  public static ApplicationFeePolicySnapshot feeFree(
      String reason, UUID decidedByUserId, Instant decidedAt) {
    ApplicationFeePolicySnapshot snapshot = new ApplicationFeePolicySnapshot();
    snapshot.policyStatus = PolicyStatus.FEE_FREE.name();
    snapshot.feeFreeReason = requiredText(reason, "Fee-free decision reason", 1000);
    if (snapshot.feeFreeReason.length() < 10) {
      throw new IllegalArgumentException(
          "Fee-free decision reason must contain at least 10 characters.");
    }
    snapshot.feePolicyDecidedByUserId =
        Objects.requireNonNull(decidedByUserId, "Fee-free decision actor is required.");
    snapshot.feePolicyDecidedAt =
        Objects.requireNonNull(decidedAt, "Fee-free decision time is required.");
    return snapshot;
  }

  public static ApplicationFeePolicySnapshot legacyUnsnapshotted() {
    ApplicationFeePolicySnapshot snapshot = new ApplicationFeePolicySnapshot();
    snapshot.policyStatus = PolicyStatus.LEGACY_UNSNAPSHOTTED.name();
    return snapshot;
  }

  public boolean requiresPayment() {
    return getPolicyStatus() == PolicyStatus.FEE_STRUCTURE;
  }

  public PolicyStatus getPolicyStatus() {
    return PolicyStatus.valueOf(policyStatus);
  }

  public UUID getFeeStructureId() {
    return feeStructureId;
  }

  public String getFeeStructureCode() {
    return feeStructureCode;
  }

  public String getFeeStructureName() {
    return feeStructureName;
  }

  public Long getFeeStructureVersion() {
    return feeStructureVersion;
  }

  public UUID getProgrammeLevelId() {
    return programmeLevelId;
  }

  public String getProgrammeLevelCode() {
    return programmeLevelCode;
  }

  public String getApplicantCategoryCode() {
    return applicantCategoryCode;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public Instant getEffectiveAt() {
    return effectiveAt;
  }

  public String getFeeFreeReason() {
    return feeFreeReason;
  }

  public UUID getFeePolicyDecidedByUserId() {
    return feePolicyDecidedByUserId;
  }

  public Instant getFeePolicyDecidedAt() {
    return feePolicyDecidedAt;
  }

  private static String requiredText(String value, String label, int maximumLength) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required.");
    String normalized = value.trim();
    if (normalized.length() > maximumLength) {
      throw new IllegalArgumentException(
          label + " must not exceed " + maximumLength + " characters.");
    }
    return normalized;
  }
}
