package zw.ac.uz.emhare.finance.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import zw.ac.uz.emhare.finance.catalogue.FinanceFeeRuleScope;

/** @author Tinashe K */
public final class FinanceBillingContracts {
    private FinanceBillingContracts() {}
    public record BillingScopeInput(@NotNull FinanceFeeRuleScope.Dimension scopeDimension,UUID referenceId,
            @Size(max=80) String referenceCode,@Size(max=200) String referenceName) {}
    public record CreateBillingEvent(@NotBlank @Size(max=80) String sourceService,@NotBlank @Size(max=160) String sourceEventType,
            @NotNull UUID sourceEventId,@NotBlank @Size(max=80) String sourceAggregateType,@NotNull UUID sourceAggregateId,
            @NotBlank @Size(max=160) String sourceLineReference,@NotNull UUID studentFinanceAccountId,@NotNull UUID feeCatalogueId,
            @NotBlank @Size(max=500) String description,@NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull Instant effectiveAt,@NotEmpty @Size(max=9) List<@Valid BillingScopeInput> scopes) {}
    public record BillingDecision(@NotBlank @Size(max=1000) String reason,@Min(0) long expectedVersion) {}
    public record PostInvoice(@NotEmpty @Size(max=200) List<@NotNull UUID> billingEventIds,@NotNull LocalDate invoiceDate,
            @NotNull LocalDate dueDate,@NotBlank @Size(max=1000) String postingReason) {}
    public record CreateBillingPolicy(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=160) String name,
            @NotBlank @Size(max=160) String sourceEventType,@NotNull UUID feeCatalogueId,@NotNull FinanceBillingPolicy.LineBasis lineBasis,
            @NotNull FinanceBillingPolicy.QuantityBasis quantityBasis,@DecimalMin("0.0001") BigDecimal fixedQuantity,
            @NotNull Instant effectiveFrom,Instant effectiveUntil) {}
    public record BillingScopeSummary(UUID id,FinanceFeeRuleScope.Dimension scopeDimension,UUID referenceId,String referenceCode,String referenceName) {}
    public record BillingEventSummary(UUID id,String eventNumber,String sourceService,String sourceEventType,UUID sourceEventId,
            String sourceAggregateType,UUID sourceAggregateId,String sourceLineReference,UUID studentFinanceAccountId,
            String accountNumber,UUID studentId,String studentNumber,UUID feeCatalogueId,String feeCode,String feeName,
            UUID feeRuleId,int feeRuleVersion,String description,BigDecimal quantity,String transactionCurrencyCode,
            BigDecimal transactionUnitAmount,BigDecimal grossTransactionAmount,BigDecimal transactionDiscountAmount,
            BigDecimal transactionAmount,String baseCurrencyCode,UUID exchangeRateId,BigDecimal baseUnitAmount,
            BigDecimal grossBaseAmount,BigDecimal baseDiscountAmount,BigDecimal baseAmount,UUID discountRuleId,
            String discountRuleCode,BigDecimal discountPercentage,Instant effectiveAt,FinanceBillingEvent.Status status,
            UUID preparedByUserId,Instant submittedAt,UUID approvedByUserId,Instant approvedAt,Instant invoicedAt,
            long version,List<BillingScopeSummary> scopes) {}
    public record InvoiceLineSummary(UUID id,int lineNumber,UUID billingEventId,String billingEventNumber,String feeCode,
            String description,BigDecimal quantity,BigDecimal grossTransactionAmount,BigDecimal transactionDiscountAmount,
            BigDecimal transactionAmount,BigDecimal grossBaseAmount,BigDecimal baseDiscountAmount,BigDecimal baseAmount,
            UUID discountRuleId,String discountRuleCode,BigDecimal discountPercentage,
            String receivableAccountCode,String revenueAccountCode) {}
    public record InvoiceSummary(UUID id,String invoiceNumber,UUID studentFinanceAccountId,String accountNumber,
            UUID studentId,String studentNumber,String transactionCurrencyCode,String baseCurrencyCode,
            BigDecimal grossTransactionAmount,BigDecimal transactionDiscountAmount,BigDecimal netTransactionAmount,
            BigDecimal grossBaseAmount,BigDecimal baseDiscountAmount,BigDecimal netBaseAmount,LocalDate invoiceDate,LocalDate dueDate,
            String status,UUID postedByUserId,Instant postedAt,long version,List<InvoiceLineSummary> lines) {}
    public record BillingPolicySummary(UUID id,String code,int policyVersion,String name,String sourceEventType,
            UUID feeCatalogueId,String feeCode,String feeName,FinanceBillingPolicy.LineBasis lineBasis,
            FinanceBillingPolicy.QuantityBasis quantityBasis,BigDecimal fixedQuantity,Instant effectiveFrom,
            Instant effectiveUntil,FinanceBillingPolicy.Status status,UUID preparedByUserId,UUID activatedByUserId,
            Instant activatedAt,long version) {}
    public record BillingRegister(List<BillingPolicySummary> billingPolicies,List<BillingEventSummary> billingEvents,List<InvoiceSummary> invoices) {}
}
