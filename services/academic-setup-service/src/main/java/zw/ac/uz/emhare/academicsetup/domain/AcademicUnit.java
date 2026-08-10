package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_units")
@SQLRestriction("deleted_at IS NULL")
public class AcademicUnit extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_unit_type_id", nullable = false)
    private AcademicUnitType academicUnitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AcademicUnit parent;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    @Column(name = "legacy_faculty_code", length = 50)
    private String legacyFacultyCode;

    @Column(name = "legacy_department_code", length = 50)
    private String legacyDepartmentCode;

    protected AcademicUnit() {
    }

    public AcademicUnit(
            AcademicUnitType academicUnitType,
            AcademicUnit parent,
            String code,
            String name,
            String legacyFacultyCode,
            String legacyDepartmentCode) {
        this.academicUnitType = academicUnitType;
        this.parent = parent;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.status = ReferenceStatus.ACTIVE;
        this.legacyFacultyCode = trimToNull(legacyFacultyCode);
        this.legacyDepartmentCode = trimToNull(legacyDepartmentCode);
    }

    public AcademicUnitType getAcademicUnitType() {
        return academicUnitType;
    }

    public AcademicUnit getParent() {
        return parent;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ReferenceStatus getStatus() {
        return status;
    }

    public String getLegacyFacultyCode() {
        return legacyFacultyCode;
    }

    public String getLegacyDepartmentCode() {
        return legacyDepartmentCode;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
