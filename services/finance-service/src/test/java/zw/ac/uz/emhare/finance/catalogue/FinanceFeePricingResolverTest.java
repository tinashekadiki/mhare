package zw.ac.uz.emhare.finance.catalogue;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRuleScope;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeCatalogueRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeRuleRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeRuleScopeRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeePricingResolver.PricingScope;

/** @author Tinashe K */
class FinanceFeePricingResolverTest {
    @Test void selectsTheOneMostSpecificEffectiveApprovedPrice(){UUID catalogueId=UUID.randomUUID();UUID programmeId=UUID.randomUUID();Instant effectiveAt=Instant.parse("2027-02-01T00:00:00Z");FinanceFeeCatalogueRepository catalogues=mock(FinanceFeeCatalogueRepository.class);FinanceFeeRuleRepository rules=mock(FinanceFeeRuleRepository.class);FinanceFeeRuleScopeRepository scopes=mock(FinanceFeeRuleScopeRepository.class);FinanceFeeCatalogue catalogue=mock(FinanceFeeCatalogue.class);when(catalogue.isDeleted()).thenReturn(false);when(catalogue.getStatus()).thenReturn(FinanceFeeCatalogue.Status.ACTIVE);when(catalogues.findById(catalogueId)).thenReturn(Optional.of(catalogue));FinanceFeeRule global=rule(effectiveAt);FinanceFeeRule programme=rule(effectiveAt);UUID globalId=global.getId();UUID programmeRuleId=programme.getId();FinanceFeeRuleScope globalScope=scope(FinanceFeeRuleScope.Dimension.GLOBAL,null,null);FinanceFeeRuleScope programmeScope=scope(FinanceFeeRuleScope.Dimension.PROGRAMME,programmeId,"BACC");when(rules.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogueId)).thenReturn(List.of(global,programme));when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(globalId)).thenReturn(List.of(globalScope));when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(programmeRuleId)).thenReturn(List.of(programmeScope));FinanceFeePricingResolver resolver=new FinanceFeePricingResolver(catalogues,rules,scopes);FinanceFeePricingResolver.ResolvedPrice resolved=resolver.resolve(catalogueId,effectiveAt,List.of(new PricingScope(FinanceFeeRuleScope.Dimension.PROGRAMME,programmeId,"BACC","Bachelor of Accountancy")));assertSame(programme,resolved.rule());}
    @Test void rejectsEquallySpecificPricingAmbiguity(){UUID catalogueId=UUID.randomUUID();Instant effectiveAt=Instant.parse("2027-02-01T00:00:00Z");FinanceFeeCatalogueRepository catalogues=mock(FinanceFeeCatalogueRepository.class);FinanceFeeRuleRepository rules=mock(FinanceFeeRuleRepository.class);FinanceFeeRuleScopeRepository scopes=mock(FinanceFeeRuleScopeRepository.class);FinanceFeeCatalogue catalogue=mock(FinanceFeeCatalogue.class);when(catalogue.isDeleted()).thenReturn(false);when(catalogue.getStatus()).thenReturn(FinanceFeeCatalogue.Status.ACTIVE);when(catalogues.findById(catalogueId)).thenReturn(Optional.of(catalogue));FinanceFeeRule first=rule(effectiveAt);FinanceFeeRule second=rule(effectiveAt);when(rules.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogueId)).thenReturn(List.of(first,second));when(scopes.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(any())).thenAnswer(invocation->List.of(scope(FinanceFeeRuleScope.Dimension.ACADEMIC_PERIOD,null,"2027-S1")));FinanceFeePricingResolver resolver=new FinanceFeePricingResolver(catalogues,rules,scopes);assertThrows(IllegalStateException.class,()->resolver.resolve(catalogueId,effectiveAt,List.of(new PricingScope(FinanceFeeRuleScope.Dimension.ACADEMIC_PERIOD,null,"2027-S1","Semester 1"))));}
    private FinanceFeeRule rule(Instant effectiveAt){FinanceFeeRule rule=mock(FinanceFeeRule.class);when(rule.getId()).thenReturn(UUID.randomUUID());when(rule.getStatus()).thenReturn(FinanceFeeRule.Status.APPROVED);when(rule.getEffectiveFrom()).thenReturn(effectiveAt.minusSeconds(3600));when(rule.getEffectiveUntil()).thenReturn(effectiveAt.plusSeconds(3600));return rule;}
    private FinanceFeeRuleScope scope(FinanceFeeRuleScope.Dimension dimension,UUID referenceId,String referenceCode){FinanceFeeRuleScope scope=mock(FinanceFeeRuleScope.class);when(scope.getScopeDimension()).thenReturn(dimension);when(scope.getReferenceId()).thenReturn(referenceId);when(scope.getReferenceCode()).thenReturn(referenceCode);return scope;}
}
