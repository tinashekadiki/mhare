package zw.ac.uz.emhare.finance.catalogue.api.model;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructureAttachment;

import zw.ac.uz.emhare.finance.catalogue.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class FinanceFeeStructureApiModels {
    private FinanceFeeStructureApiModels() { }

    public record CreateStructure(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull FinanceFeeStructure.FeeContext feeContext,
            @NotNull FinanceFeeStructure.ScopeType scopeType,
            UUID scopeReferenceId,
            @Size(max = 80) String scopeReferenceCode,
            @Size(max = 200) String scopeReferenceName,
            @NotNull UUID programmeLevelId,
            @NotBlank @Size(max = 80) String programmeLevelCode,
            @NotBlank @Size(max = 200) String programmeLevelName,
            UUID academicPeriodId,
            @Size(max = 80) String academicPeriodCode,
            @Size(max = 200) String academicPeriodName,
            @Min(1) Integer programmePeriodNumber,
            @Size(max = 80) String applicantCategoryCode,
            @NotBlank @Size(min = 3, max = 3) String transactionCurrencyCode,
            @NotNull Instant effectiveFrom,
            Instant effectiveUntil,
            @NotEmpty @Size(max = 40) List<@Valid LineInput> lines,
            @Size(max = 80) List<@Valid AttachmentInput> attachments) { }

    public record LineInput(
            UUID feeCatalogueId,
            @Size(max = 50) String feeCode,
            @Size(max = 160) String feeName,
            @Size(max = 500) String description,
            FinanceFeeCatalogue.ChargeType chargeType,
            @Size(max = 50) String receivableAccountCode,
            @Size(max = 50) String revenueAccountCode,
            @Size(max = 30) String taxCode,
            @NotNull @DecimalMin("0.01") BigDecimal amount) { }

    public record AttachmentInput(
            @NotNull UUID programmeId,
            @NotBlank @Size(max = 80) String programmeCode,
            @NotBlank @Size(max = 200) String programmeName,
            @NotNull UUID academicPeriodId,
            @NotBlank @Size(max = 80) String academicPeriodCode,
            @NotBlank @Size(max = 200) String academicPeriodName,
            @NotNull @Min(1) Integer programmePeriodNumber,
            FinanceFeeStructureAttachment.DiscountType discountType,
            @DecimalMin("0.01") BigDecimal discountValue,
            @Size(max = 500) String discountReason) { }

    public record StructureDecision(@NotBlank @Size(max = 1000) String reason, @Min(0) long expectedVersion) { }

    public record StructureLineSummary(
            UUID feeRuleId, int lineNumber, UUID feeCatalogueId, String feeCode, String feeName,
            String description, FinanceFeeCatalogue.ChargeType chargeType, String receivableAccountCode,
            String revenueAccountCode, String taxCode, BigDecimal transactionAmount, String transactionCurrencyCode,
            String baseCurrencyCode, UUID exchangeRateId, BigDecimal exchangeRateToBase,
            BigDecimal baseAmount, FinanceFeeRule.RatingStatus ratingStatus, FinanceFeeRule.Status status) { }

    public record StructureAttachmentSummary(
            UUID id, UUID programmeId, String programmeCode, String programmeName,
            UUID academicPeriodId, String academicPeriodCode, String academicPeriodName,
            Integer programmePeriodNumber, FinanceFeeStructureAttachment.DiscountType discountType,
            BigDecimal discountValue, String discountReason, BigDecimal discountAmount,
            BigDecimal discountedTotal) { }

    public record StructureSummary(
            UUID id, String code, String name, String description, FinanceFeeStructure.FeeContext feeContext,
            FinanceFeeStructure.ScopeType scopeType, UUID scopeReferenceId, String scopeReferenceCode,
            String scopeReferenceName, UUID programmeLevelId, String programmeLevelCode, String programmeLevelName,
            UUID academicPeriodId, String academicPeriodCode, String academicPeriodName,
            Integer programmePeriodNumber, String applicantCategoryCode, String transactionCurrencyCode,
            Instant effectiveFrom, Instant effectiveUntil, FinanceFeeStructure.Status status,
            UUID preparedByUserId, UUID activatedByUserId, Instant activatedAt, long version,
            List<StructureLineSummary> lines, List<StructureAttachmentSummary> attachments,
            StructureAttachmentSummary selectedAttachment) { }

    public record StructureRegister(List<StructureSummary> structures) { }

    public record ApplicationFeePricing(
            UUID id, String code, String name, FinanceFeeStructure.Status status,
            String transactionCurrencyCode, BigDecimal totalTransactionAmount) { }

    public record AcademicUnitPathItem(UUID id, @Size(max = 80) String code, @Size(max = 200) String name) { }

    public record ResolveStructure(
            @NotNull FinanceFeeStructure.FeeContext feeContext,
            @NotNull Instant effectiveAt,
            UUID academicPeriodId,
            UUID programmeId,
            List<@Valid AcademicUnitPathItem> academicUnitPath,
            UUID programmeLevelId,
            @Size(max = 80) String programmeLevelCode,
            UUID programmeTypeId,
            @Size(max = 80) String applicantCategoryCode,
            @Min(1) Integer programmePeriodNumber) { }
}
