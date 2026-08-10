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
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "modules")
@SQLRestriction("deleted_at IS NULL")
public class AcademicModule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owning_academic_unit_id", nullable = false)
    private AcademicUnit owningAcademicUnit;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "credit_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal creditValue;

    @Column(name = "academic_level", nullable = false)
    private int academicLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicOfferingStatus status;

    @Column(name = "legacy_course_code", length = 50)
    private String legacyCourseCode;

    protected AcademicModule() {
    }

    public AcademicModule(
            AcademicUnit owningAcademicUnit,
            String code,
            String name,
            String description,
            BigDecimal creditValue,
            int academicLevel,
            String legacyCourseCode) {
        this.owningAcademicUnit = owningAcademicUnit;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = description.trim();
        this.creditValue = creditValue;
        this.academicLevel = academicLevel;
        this.status = AcademicOfferingStatus.DRAFT;
        this.legacyCourseCode = trimToNull(legacyCourseCode);
    }

    public void activate(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Module was changed by another user. Refresh before retrying.");
        }
        if (status != AcademicOfferingStatus.DRAFT && status != AcademicOfferingStatus.INACTIVE) {
            throw new IllegalStateException("Only a draft or inactive Module can be activated.");
        }
        status = AcademicOfferingStatus.ACTIVE;
    }

    public AcademicUnit getOwningAcademicUnit() {
        return owningAcademicUnit;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCreditValue() {
        return creditValue;
    }

    public int getAcademicLevel() {
        return academicLevel;
    }

    public AcademicOfferingStatus getStatus() {
        return status;
    }

    public String getLegacyCourseCode() {
        return legacyCourseCode;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
