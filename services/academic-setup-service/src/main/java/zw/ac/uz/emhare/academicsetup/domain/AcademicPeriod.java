package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_periods")
@SQLRestriction("deleted_at IS NULL")
public class AcademicPeriod extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_period_type_id", nullable = false)
    private AcademicPeriodType academicPeriodType;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarStatus status;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected AcademicPeriod() {
    }

    public AcademicPeriod(
            AcademicYear academicYear,
            AcademicPeriodType academicPeriodType,
            String code,
            String name,
            LocalDate startDate,
            LocalDate endDate) {
        this.academicYear = academicYear;
        this.academicPeriodType = academicPeriodType;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CalendarStatus.DRAFT;
        this.changeReason = "Initial record creation.";
    }

    public void update(
            AcademicYear academicYear,
            AcademicPeriodType academicPeriodType,
            String code,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String changeReason,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.DRAFT
                && (!(this.academicYear == academicYear || Objects.equals(this.academicYear.getId(), academicYear.getId()))
                    || !(this.academicPeriodType == academicPeriodType || Objects.equals(this.academicPeriodType.getId(), academicPeriodType.getId()))
                    || !this.code.equalsIgnoreCase(code))) {
            throw new IllegalStateException("An open or closed academic period cannot change year, type, or code.");
        }
        this.academicYear = academicYear;
        this.academicPeriodType = academicPeriodType;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.changeReason = requireChangeReason(changeReason);
    }

    public void open(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.DRAFT) {
            throw new IllegalStateException("Only a draft academic period can be opened.");
        }
        status = CalendarStatus.OPEN;
    }

    public void close(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.OPEN) {
            throw new IllegalStateException("Only an open academic period can be closed.");
        }
        status = CalendarStatus.CLOSED;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Academic period was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException("Academic calendar change reason must contain at least 10 characters.");
        }
        return normalized;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public AcademicPeriodType getAcademicPeriodType() {
        return academicPeriodType;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public CalendarStatus getStatus() {
        return status;
    }

    public String getChangeReason() { return changeReason; }
}
