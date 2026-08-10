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
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "programmes")
@SQLRestriction("deleted_at IS NULL")
public class Programme extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owning_academic_unit_id", nullable = false)
    private AcademicUnit owningAcademicUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_type_id", nullable = false)
    private ProgrammeType programmeType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_level_id", nullable = false)
    private ProgrammeLevel programmeLevel;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "award_name", nullable = false, length = 200)
    private String awardName;

    @Column(name = "minimum_duration_periods", nullable = false)
    private int minimumDurationPeriods;

    @Column(name = "maximum_duration_periods", nullable = false)
    private int maximumDurationPeriods;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicOfferingStatus status;

    @Column(name = "legacy_programme_code", length = 50)
    private String legacyProgrammeCode;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected Programme() {
    }

    public Programme(
            AcademicUnit owningAcademicUnit,
            ProgrammeType programmeType,
            ProgrammeLevel programmeLevel,
            String code,
            String name,
            String awardName,
            int minimumDurationPeriods,
            int maximumDurationPeriods,
            String legacyProgrammeCode) {
        this.owningAcademicUnit = owningAcademicUnit;
        this.programmeType = programmeType;
        this.programmeLevel = programmeLevel;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.awardName = awardName.trim();
        this.minimumDurationPeriods = minimumDurationPeriods;
        this.maximumDurationPeriods = maximumDurationPeriods;
        this.status = AcademicOfferingStatus.DRAFT;
        this.legacyProgrammeCode = trimToNull(legacyProgrammeCode);
        this.changeReason = "Initial record creation.";
    }

    public void update(
            AcademicUnit owningAcademicUnit,
            ProgrammeType programmeType,
            ProgrammeLevel programmeLevel,
            String code,
            String name,
            String awardName,
            int minimumDurationPeriods,
            int maximumDurationPeriods,
            String legacyProgrammeCode,
            String changeReason,
            long expectedVersion) {
        requireVersion(expectedVersion);
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (status != AcademicOfferingStatus.DRAFT
                && (!(this.owningAcademicUnit == owningAcademicUnit
                        || Objects.equals(this.owningAcademicUnit.getId(), owningAcademicUnit.getId()))
                    || !this.code.equalsIgnoreCase(normalizedCode))) {
            throw new IllegalStateException("A programme that has left draft cannot change owning academic unit or code.");
        }
        this.owningAcademicUnit = owningAcademicUnit;
        this.programmeType = programmeType;
        this.programmeLevel = programmeLevel;
        this.code = normalizedCode;
        this.name = name.trim();
        this.awardName = awardName.trim();
        this.minimumDurationPeriods = minimumDurationPeriods;
        this.maximumDurationPeriods = maximumDurationPeriods;
        this.legacyProgrammeCode = trimToNull(legacyProgrammeCode);
        this.changeReason = requireChangeReason(changeReason);
    }

    public void activate(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AcademicOfferingStatus.DRAFT && status != AcademicOfferingStatus.INACTIVE) {
            throw new IllegalStateException("Only a draft or inactive programme can be activated.");
        }
        status = AcademicOfferingStatus.ACTIVE;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Programme was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException("Programme change reason must contain at least 10 characters.");
        }
        return normalized;
    }

    public AcademicUnit getOwningAcademicUnit() {
        return owningAcademicUnit;
    }

    public ProgrammeType getProgrammeType() {
        return programmeType;
    }

    public ProgrammeLevel getProgrammeLevel() {
        return programmeLevel;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAwardName() {
        return awardName;
    }

    public int getMinimumDurationPeriods() {
        return minimumDurationPeriods;
    }

    public int getMaximumDurationPeriods() {
        return maximumDurationPeriods;
    }

    public AcademicOfferingStatus getStatus() {
        return status;
    }

    public String getLegacyProgrammeCode() {
        return legacyProgrammeCode;
    }

    public String getChangeReason() {
        return changeReason;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
