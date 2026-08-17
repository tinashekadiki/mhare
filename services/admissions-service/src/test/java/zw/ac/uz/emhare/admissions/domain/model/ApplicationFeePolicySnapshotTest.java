package zw.ac.uz.emhare.admissions.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @author Tinashe K
 */
class ApplicationFeePolicySnapshotTest {
  private static final Instant EFFECTIVE_AT = Instant.parse("2027-01-15T10:00:00Z");

  @Test
  void financeStructure_shouldRetainCompleteImmutablePricingEvidence() {
    UUID structureId = UUID.randomUUID();
    UUID programmeLevelId = UUID.randomUUID();

    ApplicationFeePolicySnapshot snapshot =
        ApplicationFeePolicySnapshot.financeStructure(
            structureId,
            "APP-UG-LOCAL",
            "Local undergraduate application",
            4,
            programmeLevelId,
            "UG",
            "LOCAL",
            new BigDecimal("25.00"),
            "USD",
            EFFECTIVE_AT);

    assertEquals(
        ApplicationFeePolicySnapshot.PolicyStatus.FEE_STRUCTURE, snapshot.getPolicyStatus());
    assertEquals(structureId, snapshot.getFeeStructureId());
    assertEquals("APP-UG-LOCAL", snapshot.getFeeStructureCode());
    assertEquals(4, snapshot.getFeeStructureVersion());
    assertEquals(programmeLevelId, snapshot.getProgrammeLevelId());
    assertEquals("LOCAL", snapshot.getApplicantCategoryCode());
    assertEquals(new BigDecimal("25.00"), snapshot.getAmount());
    assertEquals("USD", snapshot.getCurrencyCode());
    assertEquals(EFFECTIVE_AT, snapshot.getEffectiveAt());
    assertTrue(snapshot.requiresPayment());
  }

  @Test
  void feeFree_shouldRetainTheAuditedDecisionWithoutInventingPricing() {
    UUID actor = UUID.randomUUID();

    ApplicationFeePolicySnapshot snapshot =
        ApplicationFeePolicySnapshot.feeFree(
            "Council-approved application-fee waiver.", actor, EFFECTIVE_AT);

    assertEquals(ApplicationFeePolicySnapshot.PolicyStatus.FEE_FREE, snapshot.getPolicyStatus());
    assertEquals("Council-approved application-fee waiver.", snapshot.getFeeFreeReason());
    assertEquals(actor, snapshot.getFeePolicyDecidedByUserId());
    assertEquals(EFFECTIVE_AT, snapshot.getFeePolicyDecidedAt());
    assertFalse(snapshot.requiresPayment());
  }

  @Test
  void financeStructure_shouldRejectIncompleteOrInvalidPricingEvidence() {
    UUID structureId = UUID.randomUUID();
    UUID programmeLevelId = UUID.randomUUID();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApplicationFeePolicySnapshot.financeStructure(
                structureId,
                "APP-UG",
                "Application",
                1,
                programmeLevelId,
                "UG",
                "LOCAL",
                BigDecimal.ZERO,
                "USD",
                EFFECTIVE_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ApplicationFeePolicySnapshot.financeStructure(
                structureId,
                "APP-UG",
                "Application",
                1,
                programmeLevelId,
                "UG",
                "LOCAL",
                new BigDecimal("25.00"),
                "usd",
                EFFECTIVE_AT));
  }
}
