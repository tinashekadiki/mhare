package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "curriculum_modules")
@SQLRestriction("deleted_at IS NULL")
public class CurriculumModule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_version_id", nullable = false)
    private ProgrammeVersion programmeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private AcademicModule academicModule;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 20)
    private CurriculumModuleType moduleType;

    @Column(name = "credit_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal creditValue;

    @Column(name = "minimum_mark_required", precision = 5, scale = 2)
    private BigDecimal minimumMarkRequired;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CurriculumModule() {
    }

    public CurriculumModule(
            ProgrammeVersion programmeVersion,
            AcademicModule academicModule,
            int periodNumber,
            CurriculumModuleType moduleType,
            BigDecimal creditValue,
            BigDecimal minimumMarkRequired,
            int sortOrder) {
        this.programmeVersion = programmeVersion;
        this.academicModule = academicModule;
        this.periodNumber = periodNumber;
        this.moduleType = moduleType;
        this.creditValue = creditValue;
        this.minimumMarkRequired = minimumMarkRequired;
        this.sortOrder = sortOrder;
    }

    public ProgrammeVersion getProgrammeVersion() {
        return programmeVersion;
    }

    public AcademicModule getAcademicModule() {
        return academicModule;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public CurriculumModuleType getModuleType() {
        return moduleType;
    }

    public BigDecimal getCreditValue() {
        return creditValue;
    }

    public BigDecimal getMinimumMarkRequired() {
        return minimumMarkRequired;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void updatePlacement(
            int newPeriodNumber,
            CurriculumModuleType newModuleType,
            BigDecimal newCreditValue,
            BigDecimal newMinimumMarkRequired,
            int newSortOrder,
            long expectedVersion) {
        requireVersion(expectedVersion);
        periodNumber = newPeriodNumber;
        moduleType = newModuleType;
        creditValue = newCreditValue;
        minimumMarkRequired = newMinimumMarkRequired;
        sortOrder = newSortOrder;
    }

    public void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Curriculum Module was changed by another user. Refresh before retrying.");
        }
    }
}
