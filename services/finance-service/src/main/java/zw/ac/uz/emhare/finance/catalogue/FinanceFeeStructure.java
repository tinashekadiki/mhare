package zw.ac.uz.emhare.finance.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.payment.ExchangeRate;

/** A complete fee schedule whose lowest applicable scope replaces its ancestors. @author Tinashe K */
@Audited
@Entity
@Table(name = "finance_fee_structures")
@SQLRestriction("deleted_at IS NULL")
public class FinanceFeeStructure extends AuditableEntity {
    public enum FeeContext { ACADEMIC, APPLICATION, ACCOMMODATION }
    public enum ScopeType { INSTITUTION, ACADEMIC_UNIT, PROGRAMME, PROGRAMME_LEVEL, PROGRAMME_TYPE, GLOBAL }
    public enum Status { DRAFT, ACTIVE, RETIRED }

    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_context", nullable = false, length = 30)
    private FeeContext feeContext;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private ScopeType scopeType;
    @Column(name = "scope_reference_id")
    private UUID scopeReferenceId;
    @Column(name = "scope_reference_code", length = 80)
    private String scopeReferenceCode;
    @Column(name = "scope_reference_name", length = 200)
    private String scopeReferenceName;
    @Column(name = "programme_level_id")
    private UUID programmeLevelId;
    @Column(name = "programme_level_code", nullable = false, length = 80)
    private String programmeLevelCode;
    @Column(name = "programme_level_name", nullable = false, length = 200)
    private String programmeLevelName;
    @Column(name = "academic_period_id")
    private UUID academicPeriodId;
    @Column(name = "academic_period_code", length = 80)
    private String academicPeriodCode;
    @Column(name = "academic_period_name", length = 200)
    private String academicPeriodName;
    @Column(name = "programme_period_number")
    private Integer programmePeriodNumber;
    @Column(name = "applicant_category_code", length = 80)
    private String applicantCategoryCode;
    @Column(name = "transaction_currency_code", nullable = false, length = 3)
    private String transactionCurrencyCode;
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
    @Column(name = "effective_until")
    private Instant effectiveUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "prepared_by_user_id", nullable = false)
    private UUID preparedByUserId;
    @Column(name = "activated_by_user_id")
    private UUID activatedByUserId;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "activation_reason", length = 1000)
    private String activationReason;
    @Column(name = "retired_by_user_id")
    private UUID retiredByUserId;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "retirement_reason", length = 1000)
    private String retirementReason;

    protected FinanceFeeStructure() {
    }

    public FinanceFeeStructure(String code, String name, String description, FeeContext feeContext,
            ScopeType scopeType, UUID scopeReferenceId, String scopeReferenceCode, String scopeReferenceName,
            UUID programmeLevelId, String programmeLevelCode, String programmeLevelName,
            UUID academicPeriodId, String academicPeriodCode, String academicPeriodName,
            Integer programmePeriodNumber, String applicantCategoryCode, String transactionCurrencyCode,
            Instant effectiveFrom, Instant effectiveUntil, UUID preparer) {
        this.code = FinanceFeeCatalogue.required(code, "Fee structure code").toUpperCase(Locale.ROOT);
        this.name = FinanceFeeCatalogue.required(name, "Fee structure name");
        this.description = FinanceFeeCatalogue.optional(description);
        this.feeContext = ObjectsRequired.require(feeContext, "Fee context");
        this.scopeType = ObjectsRequired.require(scopeType, "Fee scope type");
        this.scopeReferenceId = scopeReferenceId;
        this.scopeReferenceCode = upperOptional(scopeReferenceCode);
        this.scopeReferenceName = FinanceFeeCatalogue.optional(scopeReferenceName);
        this.programmeLevelId = ObjectsRequired.require(programmeLevelId, "Programme level");
        this.programmeLevelCode = FinanceFeeCatalogue.required(programmeLevelCode, "Programme level code")
                .toUpperCase(Locale.ROOT);
        this.programmeLevelName = FinanceFeeCatalogue.required(programmeLevelName, "Programme level name");
        this.academicPeriodId = academicPeriodId;
        this.academicPeriodCode = upperOptional(academicPeriodCode);
        this.academicPeriodName = FinanceFeeCatalogue.optional(academicPeriodName);
        this.programmePeriodNumber = programmePeriodNumber;
        this.applicantCategoryCode = upperOptional(applicantCategoryCode);
        this.transactionCurrencyCode = ExchangeRate.normalizeCurrencyCode(transactionCurrencyCode);
        this.effectiveFrom = ObjectsRequired.require(effectiveFrom, "Effective-from time");
        this.effectiveUntil = effectiveUntil;
        this.preparedByUserId = ObjectsRequired.require(preparer, "Preparer");
        validate();
        status = Status.DRAFT;
    }

    private void validate() {
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("Fee structure effective-until time must be after effective-from time.");
        }
        if (programmePeriodNumber != null && programmePeriodNumber < 1) {
            throw new IllegalArgumentException("Programme period number must be positive.");
        }
        boolean referencedScope = scopeType != ScopeType.INSTITUTION && scopeType != ScopeType.GLOBAL;
        if (referencedScope && scopeReferenceId == null && scopeReferenceCode == null) {
            throw new IllegalArgumentException("The selected fee scope requires a reference.");
        }
        if (referencedScope && scopeReferenceName == null) {
            throw new IllegalArgumentException("The selected fee scope requires a reference name.");
        }
        if (!referencedScope && (scopeReferenceId != null || scopeReferenceCode != null || scopeReferenceName != null)) {
            throw new IllegalArgumentException("Institution and global fee scopes cannot have a reference.");
        }
        if (feeContext == FeeContext.ACADEMIC) {
            if (!(scopeType == ScopeType.INSTITUTION || scopeType == ScopeType.ACADEMIC_UNIT || scopeType == ScopeType.PROGRAMME)
                    || academicPeriodId != null || academicPeriodCode != null || academicPeriodName != null
                    || programmePeriodNumber != null || applicantCategoryCode != null) {
                throw new IllegalArgumentException("Academic fee structures use an institutional, academic-unit, or programme scope. Configure period and level discounts in the standalone student-discount register.");
            }
        } else if (feeContext == FeeContext.APPLICATION) {
            if (scopeType != ScopeType.PROGRAMME_LEVEL || academicPeriodId != null || programmePeriodNumber != null) {
                throw new IllegalArgumentException("Application fees must be scoped by programme level and effective dates.");
            }
            if (!ObjectsRequired.same(scopeReferenceId, programmeLevelId)
                    || !ObjectsRequired.same(scopeReferenceCode, programmeLevelCode)
                    || !ObjectsRequired.same(scopeReferenceName, programmeLevelName)) {
                throw new IllegalArgumentException("Application fee scope must match its programme level.");
            }
            validateApplicantCategoryCode(applicantCategoryCode);
        } else if (scopeType != ScopeType.GLOBAL || academicPeriodId != null || programmePeriodNumber != null
                || applicantCategoryCode != null) {
            throw new IllegalArgumentException("Accommodation fees must use the global scope.");
        }
    }

    public void activate(UUID actor, Instant now, String reason, long expectedVersion) {
        checkVersion(expectedVersion);
        if (status != Status.DRAFT) throw new IllegalStateException("Only a draft fee structure can be activated.");
        FinanceFeeCatalogue.distinct(actor, preparedByUserId, "Fee structure activation requires an independent Finance operator.");
        activatedByUserId = actor;
        activatedAt = now;
        activationReason = FinanceFeeCatalogue.required(reason, "Activation reason");
        status = Status.ACTIVE;
    }

    public void retire(UUID actor, Instant now, String reason, long expectedVersion) {
        checkVersion(expectedVersion);
        if (status != Status.ACTIVE) throw new IllegalStateException("Only an active fee structure can be retired.");
        retiredByUserId = actor;
        retiredAt = now;
        retirementReason = FinanceFeeCatalogue.required(reason, "Retirement reason");
        status = Status.RETIRED;
    }

    private void checkVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("Fee structure changed. Refresh before retrying.");
    }

    private static String upperOptional(String value) {
        String normalized = FinanceFeeCatalogue.optional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static void validateApplicantCategoryCode(String applicantCategoryCode) {
        if (applicantCategoryCode == null) return;
        try {
            ApplicantCategoryCode.valueOf(applicantCategoryCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported applicant category: " + applicantCategoryCode, exception);
        }
    }

    private enum ApplicantCategoryCode { LOCAL, SADC, INTERNATIONAL, CLE }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public FeeContext getFeeContext() { return feeContext; }
    public ScopeType getScopeType() { return scopeType; }
    public UUID getScopeReferenceId() { return scopeReferenceId; }
    public String getScopeReferenceCode() { return scopeReferenceCode; }
    public String getScopeReferenceName() { return scopeReferenceName; }
    public UUID getProgrammeLevelId() { return programmeLevelId; }
    public String getProgrammeLevelCode() { return programmeLevelCode; }
    public String getProgrammeLevelName() { return programmeLevelName; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getAcademicPeriodName() { return academicPeriodName; }
    public Integer getProgrammePeriodNumber() { return programmePeriodNumber; }
    public String getApplicantCategoryCode() { return applicantCategoryCode; }
    public String getTransactionCurrencyCode() { return transactionCurrencyCode; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveUntil() { return effectiveUntil; }
    public Status getStatus() { return status; }
    public UUID getPreparedByUserId() { return preparedByUserId; }
    public UUID getActivatedByUserId() { return activatedByUserId; }
    public Instant getActivatedAt() { return activatedAt; }

    private static final class ObjectsRequired {
        private ObjectsRequired() { }
        static <T> T require(T value, String label) {
            if (value == null) throw new IllegalArgumentException(label + " is required.");
            return value;
        }
        static boolean same(Object left, Object right) {
            return left != null && right != null && left.toString().equalsIgnoreCase(right.toString());
        }
    }
}
