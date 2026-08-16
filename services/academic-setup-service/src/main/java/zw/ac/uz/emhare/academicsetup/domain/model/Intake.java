package zw.ac.uz.emhare.academicsetup.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "intakes")
@SQLRestriction("deleted_at IS NULL")
public class Intake extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "offer_acceptance_deadline")
    private Instant offerAcceptanceDeadline;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "orientation_date")
    private LocalDate orientationDate;

    @Column(name = "commencement_date")
    private LocalDate commencementDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarStatus status;

    @Column(name = "maximum_programme_choices", nullable = false)
    private int maximumProgrammeChoices;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected Intake() {
    }

    public Intake(AcademicYear academicYear, String code, String name, LocalDate startsOn, LocalDate endsOn) {
        this(academicYear, code, name, startsOn, endsOn, 3);
    }

    public Intake(
            AcademicYear academicYear,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            int maximumProgrammeChoices) {
        this(academicYear, code, name, startsOn, endsOn, maximumProgrammeChoices, null, null, null, null);
    }

    public Intake(
            AcademicYear academicYear,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            int maximumProgrammeChoices,
            Instant offerAcceptanceDeadline,
            LocalDate registrationDate,
            LocalDate orientationDate,
            LocalDate commencementDate) {
        this.academicYear = academicYear;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = CalendarStatus.DRAFT;
        this.maximumProgrammeChoices = requireMaximumProgrammeChoices(maximumProgrammeChoices);
        updateOfferDates(offerAcceptanceDeadline, registrationDate, orientationDate, commencementDate);
        this.changeReason = "Initial record creation.";
    }

    public void update(
            AcademicYear academicYear,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            int maximumProgrammeChoices,
            Instant offerAcceptanceDeadline,
            LocalDate registrationDate,
            LocalDate orientationDate,
            LocalDate commencementDate,
            String changeReason,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.DRAFT
                && (!(this.academicYear == academicYear || Objects.equals(this.academicYear.getId(), academicYear.getId()))
                    || !this.code.equalsIgnoreCase(code))) {
            throw new IllegalStateException("An open or closed intake cannot change academic year or code.");
        }
        this.academicYear = academicYear;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.maximumProgrammeChoices = requireMaximumProgrammeChoices(maximumProgrammeChoices);
        updateOfferDates(offerAcceptanceDeadline, registrationDate, orientationDate, commencementDate);
        this.changeReason = requireChangeReason(changeReason);
    }

    public void update(
            AcademicYear academicYear,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            int maximumProgrammeChoices,
            String changeReason,
            long expectedVersion) {
        update(academicYear, code, name, startsOn, endsOn, maximumProgrammeChoices,
                offerAcceptanceDeadline, registrationDate, orientationDate, commencementDate,
                changeReason, expectedVersion);
    }

    public void update(
            AcademicYear academicYear,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String changeReason,
            long expectedVersion) {
        update(
                academicYear, code, name, startsOn, endsOn, maximumProgrammeChoices,
                offerAcceptanceDeadline, registrationDate, orientationDate, commencementDate,
                changeReason, expectedVersion);
    }

    private void updateOfferDates(Instant acceptanceDeadline, LocalDate registration,
            LocalDate orientation, LocalDate commencement) {
        if (commencement != null && (registration != null && registration.isAfter(commencement)
                || orientation != null && orientation.isAfter(commencement))) {
            throw new IllegalArgumentException("Registration and orientation dates cannot be after commencement.");
        }
        offerAcceptanceDeadline = acceptanceDeadline;
        registrationDate = registration;
        orientationDate = orientation;
        commencementDate = commencement;
    }

    public void open(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.DRAFT) {
            throw new IllegalStateException("Only a draft intake can be opened.");
        }
        status = CalendarStatus.OPEN;
    }

    public void close(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != CalendarStatus.OPEN) {
            throw new IllegalStateException("Only an open intake can be closed.");
        }
        status = CalendarStatus.CLOSED;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Intake was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException("Academic calendar change reason must contain at least 10 characters.");
        }
        return normalized;
    }

    private int requireMaximumProgrammeChoices(int value) {
        if (value < 1 || value > 20) {
            throw new IllegalArgumentException("Maximum programme choices must be between 1 and 20.");
        }
        return value;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public Instant getOfferAcceptanceDeadline() { return offerAcceptanceDeadline; }

    public LocalDate getRegistrationDate() { return registrationDate; }

    public LocalDate getOrientationDate() { return orientationDate; }

    public LocalDate getCommencementDate() { return commencementDate; }

    public CalendarStatus getStatus() {
        return status;
    }

    public int getMaximumProgrammeChoices() {
        return maximumProgrammeChoices;
    }

    public String getChangeReason() { return changeReason; }
}
