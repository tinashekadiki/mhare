package zw.ac.uz.emhare.finance.catalogue;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class FinanceStudentDiscountContracts {
    private FinanceStudentDiscountContracts() { }

    public record CreateDiscount(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 160) String name,
            UUID academicUnitId,
            @Size(max = 80) String academicUnitCode,
            @Size(max = 200) String academicUnitName,
            @Min(0) int academicUnitDepth,
            UUID programmeId,
            @Size(max = 80) String programmeCode,
            @Size(max = 200) String programmeName,
            @NotNull UUID programmeLevelId,
            @NotBlank @Size(max = 80) String programmeLevelCode,
            @NotBlank @Size(max = 200) String programmeLevelName,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]*\\.[1-9][0-9]*$") String programmeStudyLevel,
            @NotNull FinanceStudentDiscountRule.TargetType targetType,
            UUID feeCatalogueId,
            @NotNull @DecimalMin("0.0001") @DecimalMax(value = "99.9999") BigDecimal discountPercentage,
            @NotBlank @Size(max = 500) String authorityReference,
            @NotNull Instant effectiveFrom,
            Instant effectiveUntil) { }

    public record DiscountDecision(@NotBlank @Size(max = 1000) String reason, @Min(0) long expectedVersion) { }

    public record DiscountSummary(UUID id, String code, String name, FinanceStudentDiscountRule.ScopeType scopeType,
            UUID academicUnitId, String academicUnitCode, String academicUnitName, int academicUnitDepth,
            UUID programmeId, String programmeCode, String programmeName,
            UUID programmeLevelId, String programmeLevelCode, String programmeLevelName,
            String programmeStudyLevel,
            FinanceStudentDiscountRule.TargetType targetType, UUID feeCatalogueId, String feeCode, String feeName,
            BigDecimal discountPercentage, String authorityReference,
            Instant effectiveFrom, Instant effectiveUntil, FinanceStudentDiscountRule.Status status,
            UUID preparedByUserId, UUID activatedByUserId, Instant activatedAt, long version) { }

    public record DiscountRegister(List<DiscountSummary> discounts) { }

    public record ResolveDiscount(@NotNull UUID feeCatalogueId,
            @NotNull UUID programmeId,
            @NotNull UUID academicUnitId,
            @NotNull UUID programmeLevelId,
            @NotBlank @Size(max = 80) String programmeLevelCode,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]*\\.[1-9][0-9]*$") String programmeStudyLevel,
            @NotNull Instant effectiveAt) { }

    public record AppliedDiscount(UUID id, String code, BigDecimal percentage) { }
}
