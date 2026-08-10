package zw.ac.uz.emhare.academicsetup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_years")
@SQLRestriction("deleted_at IS NULL")
public class AcademicYear extends AuditableEntity {

    @Column(nullable = false, length = 50)
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

    protected AcademicYear() {
    }

    public AcademicYear(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CalendarStatus.DRAFT;
        this.changeReason = "Initial record creation.";
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, String changeReason, long expectedVersion) {
        requireVersion(expectedVersion);
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.changeReason = requireChangeReason(changeReason);
    }

    public void open(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.DRAFT) {
            throw new IllegalStateException("Only a draft academic year can be opened.");
        }
        status = CalendarStatus.OPEN;
    }

    public void close(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.OPEN) {
            throw new IllegalStateException("Only an open academic year can be closed.");
        }
        status = CalendarStatus.CLOSED;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Academic year was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException("Academic calendar change reason must contain at least 10 characters.");
        }
        return normalized;
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
