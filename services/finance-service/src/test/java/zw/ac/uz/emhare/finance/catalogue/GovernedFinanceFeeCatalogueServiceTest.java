package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeCatalogueApiModels.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.*;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ExchangeRateRepository;

/**
 * @author Tinashe K
 */
class GovernedFinanceFeeCatalogueServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(), APPROVER = UUID.randomUUID();
  private final FinanceFeeCatalogueRepository catalogues =
      mock(FinanceFeeCatalogueRepository.class);
  private final FinanceFeeRuleRepository rules = mock(FinanceFeeRuleRepository.class);
  private final FinanceFeeRuleScopeRepository scopes = mock(FinanceFeeRuleScopeRepository.class);
  private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
  private final List<FinanceFeeCatalogue> storedCatalogues = new ArrayList<>();
  private final List<FinanceFeeRule> storedRules = new ArrayList<>();
  private final List<FinanceFeeRuleScope> storedScopes = new ArrayList<>();
  private GovernedFinanceFeeCatalogueService service;

  @BeforeEach
  void configureStores() {
    service =
        new GovernedFinanceFeeCatalogueService(
            catalogues, rules, scopes, rates, Clock.fixed(NOW, ZoneOffset.UTC));
    when(catalogues.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceFeeCatalogue value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value);
                storedCatalogues.add(value);
              }
              return value;
            });
    when(catalogues.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedCatalogues.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(catalogues.findAllByDeletedAtIsNullOrderByCodeAsc()).thenReturn(storedCatalogues);
    when(rules.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FinanceFeeRule value = inv.getArgument(0);
              if (value.getId() == null) {
                identify(value);
                storedRules.add(value);
              }
              return value;
            });
    when(rules.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(value -> value.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(rules.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(any()))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(value -> value.getFeeCatalogue().getId().equals(inv.getArgument(0)))
                    .toList());
    when(rules.findFirstByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(any()))
        .thenAnswer(
            inv ->
                storedRules.stream()
                    .filter(value -> value.getFeeCatalogue().getId().equals(inv.getArgument(0)))
                    .max(Comparator.comparingInt(FinanceFeeRule::getRuleVersion)));
    when(scopes.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<FinanceFeeRuleScope> values = inv.getArgument(0);
              storedScopes.addAll(values);
              return values;
            });
    when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(any()))
        .thenAnswer(
            inv ->
                storedScopes.stream()
                    .filter(value -> value.getFeeRule().getId().equals(inv.getArgument(0)))
                    .toList());
  }

  @Test
  void createsNormalisedCatalogueAndRetainsIndependentApprovalEvidence() {
    var catalogue =
        service.createCatalogue(
            new CreateCatalogue(
                " tuition ",
                "Tuition",
                " ",
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                " ar ",
                " revenue ",
                " VAT "),
            PREPARER);
    assertEquals("TUITION", catalogue.code());
    assertEquals("AR", catalogue.receivableAccountCode());
    assertEquals("REVENUE", catalogue.revenueAccountCode());
    assertEquals("VAT", catalogue.taxCode());
    assertNull(catalogue.description());
    assertEquals("USD", catalogue.baseCurrencyCode());
    assertEquals(FinanceFeeCatalogue.Status.DRAFT, catalogue.status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "activate", decision(0), PREPARER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "activate", decision(0), null));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "activate", decision(1), APPROVER));
    var active = service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER);
    assertEquals(APPROVER, active.activatedByUserId());
    assertEquals(NOW, active.activatedAt());
    assertEquals(active, service.register().catalogues().getFirst());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER));
    assertEquals(
        FinanceFeeCatalogue.Status.RETIRED,
        service.moveCatalogue(catalogue.id(), "retire", decision(0), APPROVER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "retire", decision(0), APPROVER));
  }

  @Test
  void rejectsDuplicateCatalogueMissingRecordsAndUnsupportedActions() {
    var catalogue = createCatalogue();
    when(catalogues.findByCodeIgnoreCaseAndDeletedAtIsNull("TUIT"))
        .thenReturn(Optional.of(storedCatalogues.getFirst()));
    assertThrows(IllegalStateException.class, this::createCatalogue);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveCatalogue(catalogue.id(), "retire", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveCatalogue(catalogue.id(), "delete", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveCatalogue(UUID.randomUUID(), "activate", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createRule(UUID.randomUUID(), ruleCommand("USD", global()), PREPARER));
    assertThrows(IllegalArgumentException.class, () -> service.applyRate(UUID.randomUUID(), 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRule(UUID.randomUUID(), "approve", decision(0), APPROVER));
  }

  @Test
  void versionsUsdPriceRulesAndRetainsExplicitScopes() {
    var catalogue = createCatalogue();
    var first = service.createRule(catalogue.id(), ruleCommand("usd", global()), PREPARER);
    var second = service.createRule(catalogue.id(), ruleCommand("USD", global()), PREPARER);
    assertEquals(1, first.ruleVersion());
    assertEquals(2, second.ruleVersion());
    assertEquals("USD", first.transactionCurrencyCode());
    assertEquals(new BigDecimal("100.00"), first.baseAmount());
    assertEquals(FinanceFeeRule.RatingStatus.RATED, first.ratingStatus());
    assertNull(first.exchangeRateId());
    assertEquals(FinanceFeeRuleScope.Dimension.GLOBAL, first.scopes().getFirst().scopeDimension());
    assertEquals(2, service.register().catalogues().getFirst().rules().size());
    verifyNoInteractions(rates);
  }

  @Test
  void approvedPriceRequiresActiveCatalogueIndependentOperatorAndExplicitScope() {
    var catalogue = createCatalogue();
    var rule =
        service.createRule(
            catalogue.id(),
            ruleCommand(
                "USD",
                List.of(
                    new ScopeInput(
                        FinanceFeeRuleScope.Dimension.PROGRAMME,
                        UUID.randomUUID(),
                        "CSC",
                        "Computing"),
                    new ScopeInput(
                        FinanceFeeRuleScope.Dimension.PROGRAMME_LEVEL,
                        null,
                        "UG",
                        "Undergraduate"))),
            PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(rule.id(), "approve", decision(0), APPROVER));
    service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(rule.id(), "approve", decision(0), PREPARER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(rule.id(), "approve", decision(1), APPROVER));
    var approved = service.moveRule(rule.id(), "approve", decision(0), APPROVER);
    assertEquals(FinanceFeeRule.Status.APPROVED, approved.status());
    assertEquals(APPROVER, approved.approvedByUserId());
    assertEquals(NOW, approved.approvedAt());
    assertTrue(approved.scopeSignature().contains("PROGRAMME_LEVEL:UG"));
    assertTrue(approved.scopeSignature().contains("|"));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(rule.id(), "approve", decision(0), APPROVER));
    assertEquals(
        FinanceFeeRule.Status.RETIRED,
        service.moveRule(rule.id(), "retire", decision(0), APPROVER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(rule.id(), "retire", decision(0), APPROVER));
  }

  @Test
  void invalidScopeConfigurationsCannotBeApproved() {
    var catalogue = createCatalogue();
    ScopeInput global = global().getFirst();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createRule(
                catalogue.id(), ruleCommand("USD", List.of(global, global)), PREPARER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createRule(
                catalogue.id(),
                ruleCommand(
                    "USD",
                    List.of(
                        global,
                        new ScopeInput(
                            FinanceFeeRuleScope.Dimension.PROGRAMME,
                            UUID.randomUUID(),
                            "CSC",
                            "Computing"))),
                PREPARER));
    var emptyScope = service.createRule(catalogue.id(), ruleCommand("USD", List.of()), PREPARER);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(emptyScope.id(), "approve", decision(0), APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRule(emptyScope.id(), "delete", decision(0), APPROVER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveRule(emptyScope.id(), "retire", decision(0), APPROVER));
  }

  @Test
  void retiredCatalogueCannotReceiveNewPricing() {
    var catalogue = createCatalogue();
    service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER);
    service.moveCatalogue(catalogue.id(), "retire", decision(0), APPROVER);
    assertThrows(
        IllegalStateException.class,
        () -> service.createRule(catalogue.id(), ruleCommand("USD", global()), PREPARER));
    assertTrue(storedRules.isEmpty());
  }

  @Test
  void unratedForeignPriceCanRecoverOnlyWithEffectiveExchangeRateEvidence() {
    var catalogue = createCatalogue();
    var created = service.createRule(catalogue.id(), ruleCommand("ZWG", global()), PREPARER);
    assertEquals(FinanceFeeRule.Status.PENDING_RATE, created.status());
    assertNull(created.baseAmount());
    assertThrows(IllegalStateException.class, () -> service.applyRate(created.id(), 0));
    ExchangeRate exchangeRate = rate();
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(exchangeRate));
    assertThrows(IllegalStateException.class, () -> service.applyRate(created.id(), 1));
    var rated = service.applyRate(created.id(), 0);
    assertEquals(FinanceFeeRule.Status.DRAFT, rated.status());
    assertEquals(exchangeRate.getId(), rated.exchangeRateId());
    assertEquals(new BigDecimal("4.57"), rated.baseAmount());
    assertEquals(FinanceFeeRule.RatingStatus.RATED, rated.ratingStatus());
    service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER);
    service.moveRule(created.id(), "approve", decision(0), APPROVER);
    assertThrows(IllegalStateException.class, () -> service.applyRate(created.id(), 0));
  }

  @Test
  void foreignPriceUsesEffectiveRateAtCreationAndRejectsAmbiguity() {
    var catalogue = createCatalogue();
    ExchangeRate rate = rate();
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(rate));
    var created = service.createRule(catalogue.id(), ruleCommand("ZWG", global()), PREPARER);
    assertEquals(new BigDecimal("4.57"), created.baseAmount());
    assertEquals(rate.getId(), created.exchangeRateId());
    when(rates.findEffectiveRates("ZWG", NOW)).thenReturn(List.of(rate, rate));
    assertThrows(
        IllegalStateException.class,
        () -> service.createRule(catalogue.id(), ruleCommand("ZWG", global()), PREPARER));
    assertEquals(1, storedRules.size());
  }

  @Test
  void usdDoesNotAcceptSyntheticRateApplication() {
    var catalogue = createCatalogue();
    var rule = service.createRule(catalogue.id(), ruleCommand("USD", global()), PREPARER);
    assertThrows(IllegalStateException.class, () -> service.applyRate(rule.id(), 0));
    assertNull(storedRules.getFirst().getExchangeRate());
    verifyNoInteractions(rates);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void rejectsNonPositiveFeeAmount(String amount) {
    var catalogue = createCatalogue();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createRule(
                catalogue.id(),
                new CreateRule("USD", new BigDecimal(amount), NOW, null, global()),
                PREPARER));
    assertTrue(storedRules.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void rejectsFeeRuleEndAtOrBeforeStart(long difference) {
    var catalogue = createCatalogue();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createRule(
                catalogue.id(),
                new CreateRule("USD", BigDecimal.ONE, NOW, NOW.plusSeconds(difference), global()),
                PREPARER));
  }

  @ParameterizedTest
  @MethodSource("invalidScopeReferences")
  void rejectsScopeReferenceContradictions(
      FinanceFeeRuleScope.Dimension dimension,
      UUID referenceId,
      String referenceCode,
      String referenceName) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FinanceFeeRuleScope(null, dimension, referenceId, referenceCode, referenceName));
  }

  static Stream<Arguments> invalidScopeReferences() {
    return Stream.of(
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, UUID.randomUUID(), null, null),
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, null, "ALL", null),
        Arguments.of(FinanceFeeRuleScope.Dimension.GLOBAL, null, null, "All"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, null, null, "Computing"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, null, " ", "Computing"),
        Arguments.of(FinanceFeeRuleScope.Dimension.PROGRAMME, UUID.randomUUID(), null, null));
  }

  @Test
  void cannotApplyIncompleteRateEvidenceOrApproveWithoutReason() {
    var catalogue = createCatalogue();
    var rule = service.createRule(catalogue.id(), ruleCommand("ZWG", global()), PREPARER);
    FinanceFeeRule domainRule = storedRules.getFirst();
    assertThrows(
        IllegalArgumentException.class, () -> domainRule.applyRate(null, BigDecimal.ONE, 0));
    assertThrows(IllegalArgumentException.class, () -> domainRule.applyRate(rate(), null, 0));
    assertThrows(
        IllegalArgumentException.class, () -> domainRule.applyRate(rate(), BigDecimal.ZERO, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> domainRule.applyRate(rate(), BigDecimal.ONE.negate(), 0));
    domainRule.applyRate(rate(), BigDecimal.ONE, 0);
    service.moveCatalogue(catalogue.id(), "activate", decision(0), APPROVER);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveRule(rule.id(), "approve", new WorkflowDecision(" ", 0), APPROVER));
    assertEquals(FinanceFeeRule.Status.DRAFT, domainRule.getStatus());
  }

  private CatalogueSummary createCatalogue() {
    return service.createCatalogue(
        new CreateCatalogue(
            "TUIT", "Tuition", null, FinanceFeeCatalogue.ChargeType.PROGRAMME, "AR", "REV", null),
        PREPARER);
  }

  private CreateRule ruleCommand(String currency, List<ScopeInput> ruleScopes) {
    return new CreateRule(currency, new BigDecimal("100.00"), NOW, null, ruleScopes);
  }

  private List<ScopeInput> global() {
    return List.of(new ScopeInput(FinanceFeeRuleScope.Dimension.GLOBAL, null, null, null));
  }

  private WorkflowDecision decision(long version) {
    return new WorkflowDecision("Independent approval evidence", version);
  }

  private ExchangeRate rate() {
    return identify(
        new ExchangeRate(
            "ZWG",
            new BigDecimal("0.045678"),
            NOW.minusSeconds(1),
            null,
            "Central bank",
            "FX-1",
            PREPARER));
  }

  private static <T extends AuditableEntity> T identify(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
