package zw.ac.uz.emhare.finance.catalogue.domain.model;

import zw.ac.uz.emhare.finance.catalogue.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** One governed percentage discount selected by scope specificity. @author Tinashe K */
@Audited
@Entity
@Table(name = "finance_student_discount_rules")
@SQLRestriction("deleted_at IS NULL")
public class FinanceStudentDiscountRule extends AuditableEntity {
    public enum ScopeType { INSTITUTION, ACADEMIC_UNIT, PROGRAMME }
    public enum TargetType { ALL_FEES, FEE_LINE }
    public enum Status { DRAFT, ACTIVE, RETIRED }

    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 30) private ScopeType scopeType;
    @Column(name = "scope_reference_id") private UUID scopeReferenceId;
    @Column(name = "scope_reference_code", length = 80) private String scopeReferenceCode;
    @Column(name = "scope_reference_name", length = 200) private String scopeReferenceName;
    @Column(name = "scope_depth", nullable = false) private int scopeDepth;
    @Column(name = "academic_unit_id") private UUID academicUnitId;
    @Column(name = "academic_unit_code", length = 80) private String academicUnitCode;
    @Column(name = "academic_unit_name", length = 200) private String academicUnitName;
    @Column(name = "programme_id") private UUID programmeId;
    @Column(name = "programme_code", length = 80) private String programmeCode;
    @Column(name = "programme_name", length = 200) private String programmeName;
    @Column(name = "programme_level_id", nullable = false) private UUID programmeLevelId;
    @Column(name = "programme_level_code", nullable = false, length = 80) private String programmeLevelCode;
    @Column(name = "programme_level_name", nullable = false, length = 200) private String programmeLevelName;
    @Column(name = "programme_study_level", nullable = false, length = 20) private String programmeStudyLevel;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 30) private TargetType targetType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fee_catalogue_id") private FinanceFeeCatalogue feeCatalogue;
    @Column(name = "discount_percentage", nullable = false, precision = 7, scale = 4) private BigDecimal discountPercentage;
    @Column(name = "authority_reference", nullable = false, length = 500) private String authorityReference;
    @Column(name = "effective_from", nullable = false) private Instant effectiveFrom;
    @Column(name = "effective_until") private Instant effectiveUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "prepared_by_user_id", nullable = false) private UUID preparedByUserId;
    @Column(name = "activated_by_user_id") private UUID activatedByUserId;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "activation_reason", length = 1000) private String activationReason;
    @Column(name = "retired_by_user_id") private UUID retiredByUserId;
    @Column(name = "retired_at") private Instant retiredAt;
    @Column(name = "retirement_reason", length = 1000) private String retirementReason;

    protected FinanceStudentDiscountRule() { }

    public FinanceStudentDiscountRule(String code, String name,
            UUID academicUnitId, String academicUnitCode, String academicUnitName, int academicUnitDepth,
            UUID programmeId, String programmeCode, String programmeName,
            UUID programmeLevelId, String programmeLevelCode, String programmeLevelName,
            String programmeStudyLevel, TargetType targetType, FinanceFeeCatalogue feeCatalogue,
            BigDecimal discountPercentage,
            String authorityReference, Instant effectiveFrom, Instant effectiveUntil, UUID preparer) {
        this.code = required(code, "Discount code").toUpperCase(Locale.ROOT);
        this.name = required(name, "Discount name");
        this.academicUnitId = academicUnitId;
        this.academicUnitCode = normalizedCode(academicUnitCode);
        this.academicUnitName = optional(academicUnitName);
        this.programmeId = programmeId;
        this.programmeCode = normalizedCode(programmeCode);
        this.programmeName = optional(programmeName);
        this.programmeLevelId = Objects.requireNonNull(programmeLevelId, "Programme level is required.");
        this.programmeLevelCode = required(programmeLevelCode, "Programme level code").toUpperCase(Locale.ROOT);
        this.programmeLevelName = required(programmeLevelName, "Programme level name");
        this.programmeStudyLevel = required(programmeStudyLevel, "Programme study level");
        if (programmeId != null) {
            this.scopeType = ScopeType.PROGRAMME;
            this.scopeReferenceId = programmeId;
            this.scopeReferenceCode = this.programmeCode;
            this.scopeReferenceName = this.programmeName;
            this.scopeDepth = 100;
        } else if (academicUnitId != null) {
            this.scopeType = ScopeType.ACADEMIC_UNIT;
            this.scopeReferenceId = academicUnitId;
            this.scopeReferenceCode = this.academicUnitCode;
            this.scopeReferenceName = this.academicUnitName;
            this.scopeDepth = academicUnitDepth;
        } else {
            this.scopeType = ScopeType.INSTITUTION;
            this.scopeDepth = 0;
        }
        this.targetType = Objects.requireNonNull(targetType, "Discount target is required.");
        this.feeCatalogue = feeCatalogue;
        this.discountPercentage = Objects.requireNonNull(discountPercentage, "Discount percentage is required.");
        this.authorityReference = required(authorityReference, "Discount authority");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "Discount effective start is required.");
        this.effectiveUntil = effectiveUntil;
        this.preparedByUserId = Objects.requireNonNull(preparer, "Discount preparer is required.");
        validate();
        this.status = Status.DRAFT;
    }

    private void validate() {
        requireCompleteSnapshot(academicUnitId, academicUnitCode, academicUnitName, "Academic unit");
        requireCompleteSnapshot(programmeId, programmeCode, programmeName, "Programme");
        if (scopeType == ScopeType.INSTITUTION) {
            if (scopeReferenceId != null || scopeReferenceCode != null || scopeReferenceName != null || scopeDepth != 0) {
                throw new IllegalArgumentException("Institution discounts cannot have an academic scope reference.");
            }
        } else if (scopeReferenceId == null || scopeReferenceCode == null || scopeReferenceName == null || scopeDepth < 1) {
            throw new IllegalArgumentException("Academic-unit and programme discounts require their scope and hierarchy depth.");
        }
        if (!programmeLevelCode.equals("UG") && !programmeLevelCode.equals("PG")) {
            throw new IllegalArgumentException("Programme level must be UG or PG.");
        }
        if (!programmeStudyLevel.matches("^[1-9][0-9]*\\.[1-9][0-9]*$")) {
            throw new IllegalArgumentException("Programme study level must use year.semester format, for example 3.1.");
        }
        if ((targetType == TargetType.ALL_FEES && feeCatalogue != null)
                || (targetType == TargetType.FEE_LINE && feeCatalogue == null)) {
            throw new IllegalArgumentException("A fee-line discount requires one fee definition; an all-fees discount cannot select one.");
        }
        if (discountPercentage.signum() <= 0 || discountPercentage.compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("Discount percentage must be greater than zero and less than 100.");
        }
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Discount end must be after its start.");
        }
    }

    private static void requireCompleteSnapshot(UUID id, String code, String name, String label) {
        boolean empty = id == null && code == null && name == null;
        boolean complete = id != null && code != null && name != null;
        if (!empty && !complete) throw new IllegalArgumentException(label + " selection must be complete.");
    }

    public void activate(UUID actor, Instant time, String reason, long expectedVersion) {
        version(expectedVersion);
        if (status != Status.DRAFT) throw new IllegalStateException("Only a draft discount can be activated.");
        if (actor == null || actor.equals(preparedByUserId)) {
            throw new IllegalStateException("Discount activation requires an independent Finance operator.");
        }
        activatedByUserId = actor; activatedAt = time; activationReason = required(reason, "Activation reason");
        status = Status.ACTIVE;
    }

    public void retire(UUID actor, Instant time, String reason, long expectedVersion) {
        version(expectedVersion);
        if (status != Status.ACTIVE) throw new IllegalStateException("Only an active discount can be retired.");
        retiredByUserId = Objects.requireNonNull(actor, "Retiring actor is required.");
        retiredAt = Objects.requireNonNull(time, "Retirement time is required.");
        retirementReason = required(reason, "Retirement reason"); status = Status.RETIRED;
    }

    public boolean appliesAt(Instant effectiveAt) {
        return !effectiveAt.isBefore(effectiveFrom) && (effectiveUntil == null || effectiveAt.isBefore(effectiveUntil));
    }

    private void version(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("Discount changed. Refresh before retrying.");
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizedCode(String value) {
        String optional = optional(value); return optional == null ? null : optional.toUpperCase(Locale.ROOT);
    }

    public String getCode() { return code; } public String getName() { return name; }
    public ScopeType getScopeType() { return scopeType; } public UUID getScopeReferenceId() { return scopeReferenceId; }
    public String getScopeReferenceCode() { return scopeReferenceCode; } public String getScopeReferenceName() { return scopeReferenceName; }
    public int getScopeDepth() { return scopeDepth; }
    public UUID getAcademicUnitId() { return academicUnitId; }
    public String getAcademicUnitCode() { return academicUnitCode; }
    public String getAcademicUnitName() { return academicUnitName; }
    public UUID getProgrammeId() { return programmeId; }
    public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
    public UUID getProgrammeLevelId() { return programmeLevelId; }
    public String getProgrammeLevelCode() { return programmeLevelCode; }
    public String getProgrammeLevelName() { return programmeLevelName; }
    public String getProgrammeStudyLevel() { return programmeStudyLevel; }
    public TargetType getTargetType() { return targetType; }
    public FinanceFeeCatalogue getFeeCatalogue() { return feeCatalogue; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public String getAuthorityReference() { return authorityReference; } public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveUntil() { return effectiveUntil; } public Status getStatus() { return status; }
    public UUID getPreparedByUserId() { return preparedByUserId; } public UUID getActivatedByUserId() { return activatedByUserId; }
    public Instant getActivatedAt() { return activatedAt; }
}
