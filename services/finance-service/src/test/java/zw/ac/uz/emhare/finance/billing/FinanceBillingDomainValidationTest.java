package zw.ac.uz.emhare.finance.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeePricingResolver.PricingScope;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

/**
 * @author Tinashe K
 */
class FinanceBillingDomainValidationTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(), APPROVER = UUID.randomUUID();
  private final FinanceFeeCatalogue catalogue =
      new FinanceFeeCatalogue(
          "TUITION",
          "Tuition",
          null,
          FinanceFeeCatalogue.ChargeType.PROGRAMME,
          "AR",
          "REV",
          null,
          PREPARER);
  private final StudentFinanceAccount account =
      new StudentFinanceAccount(
          new StudentFinanceAccountProvisioningRequestedEvent(
              UUID.randomUUID(),
              1,
              NOW,
              UUID.randomUUID(),
              UUID.randomUUID(),
              "R260001A",
              UUID.randomUUID(),
              UUID.randomUUID(),
              "student@example.test"),
          NOW);

  @ParameterizedTest
  @MethodSource("invalidFixedPolicyQuantities")
  void fixedPoliciesRejectMissingOrNonPositiveQuantity(BigDecimal quantity) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy(
                1,
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.FIXED,
                quantity,
                NOW,
                null));
  }

  static Stream<Arguments> invalidFixedPolicyQuantities() {
    return Stream.of(
        Arguments.of((BigDecimal) null),
        Arguments.of(BigDecimal.ZERO),
        Arguments.of(BigDecimal.ONE.negate()));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void policyVersionsMustBePositive(int version) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy(
                version,
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.FIXED,
                BigDecimal.ONE,
                NOW,
                null));
  }

  @Test
  void moduleCreditPolicyForbidsFixedQuantityAndRequiresActualModuleCredits() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy(
                1,
                FinanceBillingPolicy.LineBasis.REGISTERED_MODULE,
                FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE,
                BigDecimal.ONE,
                NOW,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy(
                1,
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE,
                null,
                NOW,
                null));
    var policy =
        policy(
            1,
            FinanceBillingPolicy.LineBasis.REGISTERED_MODULE,
            FinanceBillingPolicy.QuantityBasis.MODULE_CREDIT_VALUE,
            null,
            NOW,
            NOW.plusSeconds(60));
    assertThrows(NullPointerException.class, () -> policy.quantityForModule(null));
    assertEquals(new BigDecimal("15.5000"), policy.quantityForModule(new BigDecimal("15.5")));
    assertThrows(
        ArithmeticException.class, () -> policy.quantityForModule(new BigDecimal("15.12345")));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void policyEffectiveWindowMustIncrease(long seconds) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            policy(
                1,
                FinanceBillingPolicy.LineBasis.REGISTRATION,
                FinanceBillingPolicy.QuantityBasis.FIXED,
                BigDecimal.ONE,
                NOW,
                NOW.plusSeconds(seconds)));
  }

  @Test
  void policyActivationAndRetirementRequireExplanations() {
    var policy =
        policy(
            1,
            FinanceBillingPolicy.LineBasis.REGISTRATION,
            FinanceBillingPolicy.QuantityBasis.FIXED,
            BigDecimal.ONE,
            NOW,
            null);
    assertThrows(IllegalStateException.class, () -> policy.retire(APPROVER, NOW, "Retire", 0));
    assertThrows(IllegalStateException.class, () -> policy.activate(null, NOW, "Approve", 0));
    assertThrows(IllegalArgumentException.class, () -> policy.activate(APPROVER, NOW, null, 0));
    assertThrows(IllegalArgumentException.class, () -> policy.activate(APPROVER, NOW, " ", 0));
    policy.activate(APPROVER, NOW, "Independent verification", 0);
    assertThrows(IllegalArgumentException.class, () -> policy.retire(APPROVER, NOW, "", 0));
    policy.retire(APPROVER, NOW, "Superseded fee policy", 0);
    assertEquals(FinanceBillingPolicy.Status.RETIRED, policy.getStatus());
  }

  @Test
  void chargeRequiresApprovedRatedFeeAndExactQuantityPrecision() {
    FinanceFeeRule draft =
        new FinanceFeeRule(
            catalogue, 1, "USD", BigDecimal.TEN, null, BigDecimal.TEN, NOW, null, PREPARER);
    assertThrows(IllegalStateException.class, () -> event(draft, BigDecimal.ONE));
    FinanceFeeRule unrated =
        new FinanceFeeRule(catalogue, 1, "ZWG", BigDecimal.TEN, null, null, NOW, null, PREPARER);
    assertThrows(IllegalStateException.class, () -> event(unrated, BigDecimal.ONE));
    var approved = approvedRule();
    assertThrows(IllegalArgumentException.class, () -> event(approved, null));
    assertThrows(ArithmeticException.class, () -> event(approved, new BigDecimal("1.23456")));
    assertEquals(
        new BigDecimal("12.35"), event(approved, new BigDecimal("1.2345")).getTransactionAmount());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void chargeCannotLoseItsAuthoritativeSourceLine(String sourceLine) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinanceBillingEvent(
                "BILL-1",
                "student-records-service",
                "registered",
                UUID.randomUUID(),
                "REGISTRATION",
                UUID.randomUUID(),
                sourceLine,
                account,
                catalogue,
                approvedRule(),
                "Tuition",
                BigDecimal.ONE,
                NOW,
                PREPARER,
                NOW));
  }

  @ParameterizedTest
  @MethodSource("invalidInvoiceTotals")
  void invoiceRequiresPositiveGrossAndNetTotalsInBothCurrencies(
      BigDecimal grossTransaction,
      BigDecimal netTransaction,
      BigDecimal grossBase,
      BigDecimal netBase) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FinanceInvoice(
                "INV-1",
                account,
                "USD",
                grossTransaction,
                BigDecimal.ZERO,
                netTransaction,
                grossBase,
                BigDecimal.ZERO,
                netBase,
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 30),
                APPROVER,
                NOW,
                "Approved billing"));
  }

  static Stream<Arguments> invalidInvoiceTotals() {
    return Stream.of(
        Arguments.of(null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
        Arguments.of(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
        Arguments.of(BigDecimal.TEN, null, BigDecimal.TEN, BigDecimal.TEN),
        Arguments.of(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN),
        Arguments.of(BigDecimal.TEN, BigDecimal.TEN, null, BigDecimal.TEN),
        Arguments.of(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN),
        Arguments.of(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null),
        Arguments.of(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO));
  }

  @ParameterizedTest
  @MethodSource("invalidBillingScopeReferences")
  void billingScopeRejectsContradictoryOrIncompleteReferenceEvidence(
      FinanceFeeRuleScope.Dimension dimension, UUID id, String code, String name) {
    var event = event(approvedRule(), BigDecimal.ONE);
    assertThrows(
        IllegalArgumentException.class,
        () -> new FinanceBillingEventScope(event, new PricingScope(dimension, id, code, name)));
  }

  static Stream<Arguments> invalidBillingScopeReferences() {
    return Stream.of(
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, UUID.randomUUID(), null, null),
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, null, "ALL", null),
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, null, null, "All"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, null, null, "Computing"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, null, " ", "Computing"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, UUID.randomUUID(), null, " "));
  }

  @Test
  void billingScopePreservesUuidOrNormalisedCodeAndGlobalHasNoSyntheticReference() {
    var event = event(approvedRule(), BigDecimal.ONE);
    var global =
        new FinanceBillingEventScope(
            event, new PricingScope(FinanceFeeRuleScope.Dimension.GLOBAL, null, " ", " "));
    assertNull(global.getReferenceId());
    assertNull(global.getReferenceCode());
    UUID programmeId = UUID.randomUUID();
    var byId =
        new FinanceBillingEventScope(
            event,
            new PricingScope(
                FinanceFeeRuleScope.Dimension.PROGRAMME, programmeId, null, " Computing "));
    assertEquals(programmeId, byId.getReferenceId());
    assertNull(byId.getReferenceCode());
    assertEquals("Computing", byId.getReferenceName());
    var byCode =
        new FinanceBillingEventScope(
            event,
            new PricingScope(FinanceFeeRuleScope.Dimension.PROGRAMME, null, " csc ", "Computing"));
    assertEquals("CSC", byCode.getReferenceCode());
  }

  private FinanceBillingPolicy policy(
      int version,
      FinanceBillingPolicy.LineBasis lineBasis,
      FinanceBillingPolicy.QuantityBasis quantityBasis,
      BigDecimal quantity,
      Instant from,
      Instant until) {
    return new FinanceBillingPolicy(
        "REG-TUIT",
        version,
        "Tuition",
        "registration-confirmed",
        catalogue,
        lineBasis,
        quantityBasis,
        quantity,
        from,
        until,
        PREPARER);
  }

  private FinanceFeeRule approvedRule() {
    if (catalogue.getStatus() == FinanceFeeCatalogue.Status.DRAFT)
      catalogue.activate(APPROVER, NOW, "Independent approval", 0);
    FinanceFeeRule rule =
        new FinanceFeeRule(
            catalogue, 1, "USD", BigDecimal.TEN, null, BigDecimal.TEN, NOW, null, PREPARER);
    rule.approve(APPROVER, NOW, "Independent pricing approval", "GLOBAL:*", 0);
    return rule;
  }

  private FinanceBillingEvent event(FinanceFeeRule rule, BigDecimal quantity) {
    return new FinanceBillingEvent(
        "BILL-1",
        "student-records-service",
        "registered",
        UUID.randomUUID(),
        "REGISTRATION",
        UUID.randomUUID(),
        "tuition",
        account,
        catalogue,
        rule,
        "Tuition charge",
        quantity,
        NOW,
        PREPARER,
        NOW);
  }
}
