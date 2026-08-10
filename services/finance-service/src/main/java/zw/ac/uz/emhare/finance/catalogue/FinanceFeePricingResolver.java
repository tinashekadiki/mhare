package zw.ac.uz.emhare.finance.catalogue;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Selects one most-specific approved fee rule for an explicit billing scope. @author Tinashe K */
@Service
public class FinanceFeePricingResolver {
    private final FinanceFeeCatalogueRepository catalogueRepository;
    private final FinanceFeeRuleRepository ruleRepository;
    private final FinanceFeeRuleScopeRepository scopeRepository;

    public FinanceFeePricingResolver(FinanceFeeCatalogueRepository catalogueRepository,
            FinanceFeeRuleRepository ruleRepository,FinanceFeeRuleScopeRepository scopeRepository) {
        this.catalogueRepository=catalogueRepository;this.ruleRepository=ruleRepository;this.scopeRepository=scopeRepository;
    }

    @Transactional(readOnly=true)
    public ResolvedPrice resolve(UUID catalogueId,Instant effectiveAt,List<PricingScope> eventScopes) {
        FinanceFeeCatalogue catalogue=requireActiveCatalogue(catalogueId);
        validateEventScopes(eventScopes);
        List<RuleMatch> matches=ruleRepository.findAllByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogueId).stream()
                .filter(rule->rule.getStatus()==FinanceFeeRule.Status.APPROVED)
                .filter(rule->!effectiveAt.isBefore(rule.getEffectiveFrom())&&(rule.getEffectiveUntil()==null||effectiveAt.isBefore(rule.getEffectiveUntil())))
                .map(rule->match(rule,eventScopes)).filter(Objects::nonNull).toList();
        if(matches.isEmpty())throw new IllegalStateException("No approved effective price matches every required billing scope.");
        int highestSpecificity=matches.stream().mapToInt(RuleMatch::specificity).max().orElseThrow();
        List<RuleMatch> mostSpecific=matches.stream().filter(match->match.specificity()==highestSpecificity).toList();
        if(mostSpecific.size()!=1)throw new IllegalStateException("Multiple equally specific approved prices match this billing event. Finance must resolve the pricing ambiguity.");
        return new ResolvedPrice(catalogue,mostSpecific.getFirst().rule());
    }

    @Transactional(readOnly=true)
    public FinanceFeeCatalogue requireActiveCatalogue(UUID catalogueId){FinanceFeeCatalogue catalogue=catalogueRepository.findById(catalogueId).filter(item->!item.isDeleted()).orElseThrow(()->new IllegalArgumentException("Fee definition was not found."));if(catalogue.getStatus()!=FinanceFeeCatalogue.Status.ACTIVE)throw new IllegalStateException("Billing requires an active fee definition.");return catalogue;}

    private RuleMatch match(FinanceFeeRule rule,List<PricingScope> eventScopes) {
        List<FinanceFeeRuleScope> requiredScopes=scopeRepository.findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(rule.getId());
        if(requiredScopes.isEmpty())return null;
        if(requiredScopes.size()==1&&requiredScopes.getFirst().getScopeDimension()==FinanceFeeRuleScope.Dimension.GLOBAL)return new RuleMatch(rule,0);
        boolean matches=requiredScopes.stream().allMatch(required->eventScopes.stream().anyMatch(actual->sameReference(required,actual)));
        return matches?new RuleMatch(rule,requiredScopes.size()):null;
    }

    private boolean sameReference(FinanceFeeRuleScope required,PricingScope actual) {
        if(required.getScopeDimension()!=actual.scopeDimension())return false;
        if(required.getReferenceId()!=null)return required.getReferenceId().equals(actual.referenceId());
        return required.getReferenceCode()!=null&&actual.referenceCode()!=null&&required.getReferenceCode().equalsIgnoreCase(actual.referenceCode());
    }

    private void validateEventScopes(List<PricingScope> scopes) {
        if(scopes==null||scopes.isEmpty())throw new IllegalArgumentException("Billing event requires at least one explicit scope.");
        if(scopes.stream().map(PricingScope::scopeDimension).distinct().count()!=scopes.size())throw new IllegalArgumentException("Billing event can contain each scope dimension only once.");
        if(scopes.stream().anyMatch(scope->scope.scopeDimension()==FinanceFeeRuleScope.Dimension.GLOBAL)&&scopes.size()>1)throw new IllegalArgumentException("Global billing scope cannot be combined with another scope.");
    }

    public record PricingScope(FinanceFeeRuleScope.Dimension scopeDimension,UUID referenceId,String referenceCode,String referenceName) {}
    public record ResolvedPrice(FinanceFeeCatalogue catalogue,FinanceFeeRule rule) {}
    private record RuleMatch(FinanceFeeRule rule,int specificity) {}
}
