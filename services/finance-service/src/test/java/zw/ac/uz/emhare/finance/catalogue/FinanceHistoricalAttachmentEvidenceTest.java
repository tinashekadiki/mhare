package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructureAttachment;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructureAttachment.DiscountType;

/**
 * Tests historical discount evidence, not the retired attachment-capture workflow. @author Tinashe
 * K
 */
class FinanceHistoricalAttachmentEvidenceTest {
  private static final UUID PROGRAMME = UUID.randomUUID(),
      PERIOD = UUID.randomUUID(),
      LEVEL = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");

  @Test
  void historicalPercentageAndAmountEvidenceCalculatesDeterministicBoundedTotals() {
    assertEquals(
        new BigDecimal("12.35"),
        attachment(1, DiscountType.PERCENTAGE, new BigDecimal("12.345"), "Council authority")
            .discountAmount(new BigDecimal("100")));
    assertEquals(
        new BigDecimal("30.00"),
        attachment(1, DiscountType.AMOUNT, new BigDecimal("30"), "Council authority")
            .discountAmount(new BigDecimal("100")));
    assertEquals(
        new BigDecimal("100.00"),
        attachment(1, DiscountType.AMOUNT, new BigDecimal("150"), "Council authority")
            .discountAmount(new BigDecimal("100")));
    assertEquals(
        new BigDecimal("100.00"),
        attachment(1, DiscountType.PERCENTAGE, new BigDecimal("100"), "Council authority")
            .discountAmount(new BigDecimal("100")));
    assertEquals(
        BigDecimal.ZERO, attachment(1, null, null, null).discountAmount(new BigDecimal("100")));
  }

  @Test
  void attachmentMatchesOnlyItsExactProgrammeAcademicPeriodAndProgrammePeriod() {
    var attachment = attachment(2, DiscountType.PERCENTAGE, BigDecimal.TEN, "Council authority");
    assertTrue(attachment.matches(PROGRAMME, PERIOD, 2));
    assertFalse(attachment.matches(UUID.randomUUID(), PERIOD, 2));
    assertFalse(attachment.matches(PROGRAMME, UUID.randomUUID(), 2));
    assertFalse(attachment.matches(PROGRAMME, PERIOD, 1));
    assertFalse(attachment.matches(null, PERIOD, 2));
    assertFalse(attachment.matches(PROGRAMME, null, 2));
    assertFalse(attachment.matches(PROGRAMME, PERIOD, null));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {0, -1})
  void requiresPositiveProgrammePeriod(Integer period) {
    assertThrows(IllegalArgumentException.class, () -> attachment(period, null, null, null));
  }

  @ParameterizedTest
  @MethodSource("incompleteDiscountEvidence")
  void refusesPartialOrInvalidHistoricalDiscountEvidence(
      DiscountType type, BigDecimal value, String reason) {
    assertThrows(IllegalArgumentException.class, () -> attachment(1, type, value, reason));
  }

  static Stream<Arguments> incompleteDiscountEvidence() {
    return Stream.of(
        Arguments.of(DiscountType.PERCENTAGE, null, null),
        Arguments.of(null, BigDecimal.TEN, null),
        Arguments.of(null, null, "Authority"),
        Arguments.of(DiscountType.PERCENTAGE, BigDecimal.TEN, null),
        Arguments.of(DiscountType.PERCENTAGE, BigDecimal.ZERO, "Authority"),
        Arguments.of(DiscountType.AMOUNT, BigDecimal.ONE.negate(), "Authority"),
        Arguments.of(DiscountType.PERCENTAGE, new BigDecimal("100.01"), "Authority"));
  }

  @Test
  void attachmentCannotBeAppliedToNonAcademicFees() {
    var applicationStructure =
        new FinanceFeeStructure(
            "APP",
            "Application",
            null,
            FinanceFeeStructure.FeeContext.APPLICATION,
            FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL,
            LEVEL,
            "UG",
            "Undergraduate",
            LEVEL,
            "UG",
            "Undergraduate",
            null,
            null,
            null,
            null,
            "LOCAL",
            "USD",
            NOW,
            null,
            UUID.randomUUID());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinanceFeeStructureAttachment(
                applicationStructure,
                PROGRAMME,
                "CSC",
                "Computing",
                PERIOD,
                "2026-S1",
                "Semester one",
                1,
                null,
                null,
                null));
  }

  private FinanceFeeStructureAttachment attachment(
      Integer period, DiscountType type, BigDecimal value, String reason) {
    return new FinanceFeeStructureAttachment(
        new FinanceFeeStructure(
            "ACADEMIC",
            "Academic",
            null,
            FinanceFeeStructure.FeeContext.ACADEMIC,
            FinanceFeeStructure.ScopeType.INSTITUTION,
            null,
            null,
            null,
            LEVEL,
            "UG",
            "Undergraduate",
            null,
            null,
            null,
            null,
            null,
            "USD",
            NOW,
            null,
            UUID.randomUUID()),
        PROGRAMME,
        "CSC",
        "Computing",
        PERIOD,
        "2026-S1",
        "Semester one",
        period,
        type,
        value,
        reason);
  }
}
