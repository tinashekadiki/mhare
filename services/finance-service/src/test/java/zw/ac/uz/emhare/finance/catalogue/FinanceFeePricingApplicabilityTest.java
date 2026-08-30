package zw.ac.uz.emhare.finance.catalogue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeePricingResolver.PricingScope;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class FinanceFeePricingApplicabilityTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private static final UUID PREPARER = UUID.randomUUID(),
      APPROVER = UUID.randomUUID(),
      PROGRAMME = UUID.randomUUID();
  private final FinanceFeeCatalogueRepository catalogues =
      mock(FinanceFeeCatalogueRepository.class);
  private final FinanceFeeRuleRepository rules = mock(FinanceFeeRuleRepository.class);
  private final FinanceFeeRuleScopeRepository scopes = mock(FinanceFeeRuleScopeRepository.class);
  private final FinanceFeePricingResolver resolver =
      new FinanceFeePricingResolver(catalogues, rules, scopes);
  private FinanceFeeCatalogue catalogue;

  @BeforeEach
  void configureActiveCatalogue() {
    catalogue =
        identify(
            new FinanceFeeCatalogue(
                "TUIT",
                "Tuition",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                PREPARER));
    catalogue.activate(APPROVER, NOW, "Independent approval", 0);
    when(catalogues.findById(catalogue.getId())).thenReturn(Optional.of(catalogue));
  }

  @Test
  void missingDeletedDraftAndRetiredCataloguesCannotSupplyBillingPrices() {
    assertThrows(
        IllegalArgumentException.class, () -> resolver.requireActiveCatalogue(UUID.randomUUID()));
    var draft =
        identify(
            new FinanceFeeCatalogue(
                "DRAFT",
                "Draft",
                null,
                FinanceFeeCatalogue.ChargeType.PROGRAMME,
                "AR",
                "REV",
                null,
                PREPARER));
    when(catalogues.findById(draft.getId())).thenReturn(Optional.of(draft));
    assertThrows(IllegalStateException.class, () -> resolver.requireActiveCatalogue(draft.getId()));
    catalogue.retire(APPROVER, NOW, "Retired catalogue", 0);
    assertThrows(
        IllegalStateException.class, () -> resolver.requireActiveCatalogue(catalogue.getId()));
    catalogue.markDeleted(PREPARER);
    assertThrows(
        IllegalArgumentException.class, () -> resolver.requireActiveCatalogue(catalogue.getId()));
  }

  @Test
  void eventScopesMustBePresentUniqueAndGlobalCannotCombineWithOtherScope() {
    assertThrows(
        IllegalArgumentException.class, () -> resolver.resolve(catalogue.getId(), NOW, null));
    assertThrows(
        IllegalArgumentException.class, () -> resolver.resolve(catalogue.getId(), NOW, List.of()));
    PricingScope programme =
        new PricingScope(FinanceFeeRuleScope.Dimension.PROGRAMME, PROGRAMME, "CSC", "Computing");
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(catalogue.getId(), NOW, List.of(programme, programme)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            resolver.resolve(
                catalogue.getId(),
                NOW,
                List.of(
                    programme,
                    new PricingScope(FinanceFeeRuleScope.Dimension.GLOBAL, null, null, null))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "draft",
        "future",
        "expired",
        "no-scopes",
        "different-dimension",
        "different-id",
        "different-code",
        "missing-code"
      })
  void priceCannotBeUsedWithoutEveryEffectiveApprovedApplicabilityRequirement(String scenario) {
    FinanceFeeRule rule =
        identify(
            new FinanceFeeRule(
                catalogue,
                1,
                "USD",
                BigDecimal.TEN,
                null,
                BigDecimal.TEN,
                "future".equals(scenario) ? NOW.plusSeconds(1) : NOW.minusSeconds(60),
                "expired".equals(scenario) ? NOW : null,
                PREPARER));
    if (!"draft".equals(scenario))
      rule.approve(APPROVER, NOW, "Approved pricing", "PROGRAMME:CSC", 0);
    when(rules.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogue.getId()))
        .thenReturn(List.of(rule));
    boolean codeOnly = "different-code".equals(scenario) || "missing-code".equals(scenario);
    when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(rule.getId()))
        .thenReturn(
            "no-scopes".equals(scenario)
                ? List.of()
                : List.of(
                    new FinanceFeeRuleScope(
                        rule,
                        FinanceFeeRuleScope.Dimension.PROGRAMME,
                        codeOnly ? null : PROGRAMME,
                        "CSC",
                        "Computing")));
    PricingScope actual =
        new PricingScope(
            "different-dimension".equals(scenario)
                ? FinanceFeeRuleScope.Dimension.MODULE
                : FinanceFeeRuleScope.Dimension.PROGRAMME,
            "different-id".equals(scenario) ? UUID.randomUUID() : PROGRAMME,
            "different-code".equals(scenario)
                ? "BACC"
                : "missing-code".equals(scenario) ? null : "CSC",
            "Computing");
    assertThrows(
        IllegalStateException.class,
        () -> resolver.resolve(catalogue.getId(), NOW, List.of(actual)));
  }

  @Test
  void exactStartOpenEndedPriceResolvesByCaseInsensitiveCode() {
    FinanceFeeRule rule =
        identify(
            new FinanceFeeRule(
                catalogue, 1, "USD", BigDecimal.TEN, null, BigDecimal.TEN, NOW, null, PREPARER));
    rule.approve(APPROVER, NOW, "Approved pricing", "PROGRAMME:CSC", 0);
    when(rules.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogue.getId()))
        .thenReturn(List.of(rule));
    when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(rule.getId()))
        .thenReturn(
            List.of(
                new FinanceFeeRuleScope(
                    rule, FinanceFeeRuleScope.Dimension.PROGRAMME, null, "CSC", "Computing")));
    var resolved =
        resolver.resolve(
            catalogue.getId(),
            NOW,
            List.of(
                new PricingScope(
                    FinanceFeeRuleScope.Dimension.PROGRAMME, null, "csc", "Computing")));
    assertSame(rule, resolved.rule());
    assertSame(catalogue, resolved.catalogue());
  }

  private static <T extends AuditableEntity> T identify(T entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    return entity;
  }
}
