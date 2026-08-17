package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationFeePolicySnapshot;

/**
 * Immutable application-fee policy evidence exposed to authorised application views. @author
 * Tinashe K
 */
public record ApplicationFeePolicySummary(
    String policyStatus,
    UUID feeStructureId,
    String feeStructureCode,
    String feeStructureName,
    Long feeStructureVersion,
    UUID programmeLevelId,
    String programmeLevelCode,
    String applicantCategoryCode,
    BigDecimal amount,
    String currencyCode,
    Instant effectiveAt,
    String feeFreeReason,
    UUID feePolicyDecidedByUserId,
    Instant feePolicyDecidedAt) {

  static ApplicationFeePolicySummary from(ApplicationFeePolicySnapshot snapshot) {
    if (snapshot == null) return null;
    return new ApplicationFeePolicySummary(
        snapshot.getPolicyStatus().name(),
        snapshot.getFeeStructureId(),
        snapshot.getFeeStructureCode(),
        snapshot.getFeeStructureName(),
        snapshot.getFeeStructureVersion(),
        snapshot.getProgrammeLevelId(),
        snapshot.getProgrammeLevelCode(),
        snapshot.getApplicantCategoryCode(),
        snapshot.getAmount(),
        snapshot.getCurrencyCode(),
        snapshot.getEffectiveAt(),
        snapshot.getFeeFreeReason(),
        snapshot.getFeePolicyDecidedByUserId(),
        snapshot.getFeePolicyDecidedAt());
  }
}
