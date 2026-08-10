package zw.ac.uz.emhare.finance.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class FinanceFeeCatalogueContracts {
    private FinanceFeeCatalogueContracts() {}
    public record CreateCatalogue(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=160) String name,
            @Size(max=1000) String description,@NotNull FinanceFeeCatalogue.ChargeType chargeType,
            @NotBlank @Size(max=50) String receivableAccountCode,@NotBlank @Size(max=50) String revenueAccountCode,
            @Size(max=30) String taxCode) {}
    public record WorkflowDecision(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record ScopeInput(@NotNull FinanceFeeRuleScope.Dimension scopeDimension,UUID referenceId,
            @Size(max=80) String referenceCode,@Size(max=200) String referenceName) {}
    public record CreateRule(@NotBlank @Size(min=3,max=3) String transactionCurrencyCode,
            @NotNull @DecimalMin("0.01") BigDecimal transactionAmount,@NotNull Instant effectiveFrom,
            Instant effectiveUntil,@NotEmpty @Size(max=9) List<@Valid ScopeInput> scopes) {}
    public record ScopeSummary(UUID id,FinanceFeeRuleScope.Dimension scopeDimension,UUID referenceId,String referenceCode,String referenceName) {}
    public record RuleSummary(UUID id,int ruleVersion,String transactionCurrencyCode,BigDecimal transactionAmount,
            String baseCurrencyCode,UUID exchangeRateId,BigDecimal baseAmount,FinanceFeeRule.RatingStatus ratingStatus,
            Instant effectiveFrom,Instant effectiveUntil,String scopeSignature,FinanceFeeRule.Status status,
            UUID preparedByUserId,UUID approvedByUserId,Instant approvedAt,long version,List<ScopeSummary> scopes) {}
    public record CatalogueSummary(UUID id,String code,String name,String description,FinanceFeeCatalogue.ChargeType chargeType,
            String receivableAccountCode,String revenueAccountCode,String taxCode,String baseCurrencyCode,
            FinanceFeeCatalogue.Status status,UUID preparedByUserId,UUID activatedByUserId,Instant activatedAt,
            long version,List<RuleSummary> rules) {}
    public record CatalogueRegister(List<CatalogueSummary> catalogues) {}
}
