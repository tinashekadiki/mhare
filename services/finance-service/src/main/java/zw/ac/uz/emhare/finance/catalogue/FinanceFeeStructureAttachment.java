package zw.ac.uz.emhare.finance.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Programme-period attachment for academic fee discounts. @author Tinashe K */
@Audited
@Entity
@Table(name = "finance_fee_structure_attachments")
@SQLRestriction("deleted_at IS NULL")
public class FinanceFeeStructureAttachment extends AuditableEntity {
    public enum DiscountType { PERCENTAGE, AMOUNT }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_structure_id", nullable = false)
    private FinanceFeeStructure feeStructure;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_code", nullable = false, length = 80)
    private String programmeCode;
    @Column(name = "programme_name", nullable = false, length = 200)
    private String programmeName;
    @Column(name = "academic_period_id", nullable = false)
    private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 80)
    private String academicPeriodCode;
    @Column(name = "academic_period_name", nullable = false, length = 200)
    private String academicPeriodName;
    @Column(name = "programme_period_number", nullable = false)
    private Integer programmePeriodNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private DiscountType discountType;
    @Column(name = "discount_value", precision = 19, scale = 4)
    private BigDecimal discountValue;
    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    protected FinanceFeeStructureAttachment() {
    }

    public FinanceFeeStructureAttachment(FinanceFeeStructure feeStructure, UUID programmeId, String programmeCode,
            String programmeName, UUID academicPeriodId, String academicPeriodCode, String academicPeriodName,
            Integer programmePeriodNumber, DiscountType discountType, BigDecimal discountValue, String discountReason) {
        this.feeStructure = Objects.requireNonNull(feeStructure, "Fee structure is required.");
        this.programmeId = Objects.requireNonNull(programmeId, "Programme is required.");
        this.programmeCode = FinanceFeeCatalogue.required(programmeCode, "Programme code").toUpperCase(Locale.ROOT);
        this.programmeName = FinanceFeeCatalogue.required(programmeName, "Programme name");
        this.academicPeriodId = Objects.requireNonNull(academicPeriodId, "Academic period is required.");
        this.academicPeriodCode = FinanceFeeCatalogue.required(academicPeriodCode, "Academic period code")
                .toUpperCase(Locale.ROOT);
        this.academicPeriodName = FinanceFeeCatalogue.required(academicPeriodName, "Academic period name");
        this.programmePeriodNumber = programmePeriodNumber;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountReason = FinanceFeeCatalogue.optional(discountReason);
        validate();
    }

    private void validate() {
        if (feeStructure.getFeeContext() != FinanceFeeStructure.FeeContext.ACADEMIC) {
            throw new IllegalArgumentException("Only academic fee structures can have programme-period attachments.");
        }
        if (programmePeriodNumber == null || programmePeriodNumber < 1) {
            throw new IllegalArgumentException("Programme period number must be positive.");
        }
        if (discountType == null || discountValue == null) {
            if (discountType != null || discountValue != null || discountReason != null) {
                throw new IllegalArgumentException("Discount type, value, and reason must be completed together.");
            }
            return;
        }
        if (discountReason == null) {
            throw new IllegalArgumentException("Discount reason is required.");
        }
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero.");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
    }

    public boolean matches(UUID requestedProgrammeId, UUID requestedAcademicPeriodId, Integer requestedProgrammePeriodNumber) {
        return Objects.equals(programmeId, requestedProgrammeId)
                && Objects.equals(academicPeriodId, requestedAcademicPeriodId)
                && Objects.equals(programmePeriodNumber, requestedProgrammePeriodNumber);
    }

    public BigDecimal discountAmount(BigDecimal structureTotal) {
        if (discountType == null || discountValue == null) return BigDecimal.ZERO;
        if (discountType == DiscountType.PERCENTAGE) {
            return structureTotal.multiply(discountValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return discountValue.min(structureTotal).setScale(2, RoundingMode.HALF_UP);
    }

    public FinanceFeeStructure getFeeStructure() { return feeStructure; }
    public UUID getProgrammeId() { return programmeId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getAcademicPeriodName() { return academicPeriodName; }
    public Integer getProgrammePeriodNumber() { return programmePeriodNumber; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public String getDiscountReason() { return discountReason; }
}
