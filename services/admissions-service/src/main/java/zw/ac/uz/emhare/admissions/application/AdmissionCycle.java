package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "admission_cycles", uniqueConstraints = @UniqueConstraint(name = "uk_admission_cycles_code", columnNames = "code"))
public class AdmissionCycle extends AuditableEntity {

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "intake_id", nullable = false)
    private UUID intakeId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionCycleStatus status;

    @Column(name = "maximum_programme_choices", nullable = false)
    private int maximumProgrammeChoices = 3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_type_id")
    private ApplicationType applicationType;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    protected AdmissionCycle() {
    }

    public AdmissionCycle(UUID academicYearId, UUID intakeId, String code, String name, Instant opensAt, Instant closesAt) {
        this(academicYearId, intakeId, code, name, opensAt, closesAt, null);
    }

    public AdmissionCycle(
            UUID academicYearId,
            UUID intakeId,
            String code,
            String name,
            Instant opensAt,
            Instant closesAt,
            ApplicationType applicationType) {
        this.academicYearId = academicYearId;
        this.intakeId = intakeId;
        this.code = code;
        this.name = name;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.applicationType = applicationType;
        this.status = AdmissionCycleStatus.DRAFT;
        this.changeReason = "Initial record creation.";
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getIntakeId() {
        return intakeId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public AdmissionCycleStatus getStatus() {
        return status;
    }

    public int getMaximumProgrammeChoices() {
        return maximumProgrammeChoices;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void update(
            UUID academicYearId,
            UUID intakeId,
            String code,
            String name,
            Instant opensAt,
            Instant closesAt,
            int maximumProgrammeChoices,
            ApplicationType applicationType,
            String changeReason,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != AdmissionCycleStatus.DRAFT) {
            throw new IllegalStateException("Only a draft admission cycle can be edited.");
        }
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("Admission cycle close date must be after the open date.");
        }
        this.academicYearId = academicYearId;
        this.intakeId = intakeId;
        this.code = code.trim();
        this.name = name.trim();
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.applicationType = applicationType;
        configureMaximumProgrammeChoices(maximumProgrammeChoices);
        this.changeReason = requireChangeReason(changeReason);
    }

    public void configureMaximumProgrammeChoices(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Maximum programme choices must be at least 1.");
        }
        this.maximumProgrammeChoices = value;
    }

    public void synchronizeIntakeProjection(
            UUID academicYearId,
            String intakeCode,
            String intakeName,
            Instant opensAt,
            Instant closesAt,
            int maximumProgrammeChoices) {
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("Intake end date must be after its start date.");
        }
        this.academicYearId = academicYearId;
        this.code = intakeCode.trim();
        this.name = intakeName.trim();
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        configureMaximumProgrammeChoices(maximumProgrammeChoices);
        this.applicationType = null;
        this.changeReason = "Synchronized from the Academic Setup intake source of truth.";
    }

    public void synchronizeOpenApplicationWindow() {
        status = AdmissionCycleStatus.OPEN;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException(
                    "Admission cycle was changed by another user. Refresh before retrying.");
        }
    }

    private String requireChangeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 10) {
            throw new IllegalArgumentException(
                    "Admission cycle change reason must contain at least 10 characters.");
        }
        return normalized;
    }

    public boolean isAcceptingApplicationsAt(Instant instant) {
        return status == AdmissionCycleStatus.OPEN
                && !instant.isBefore(opensAt)
                && !instant.isAfter(closesAt);
    }

    public void open(Instant instant) {
        if (status != AdmissionCycleStatus.DRAFT) {
            throw new IllegalStateException("Only a draft admission cycle can be opened.");
        }
        if (instant.isBefore(opensAt) || instant.isAfter(closesAt)) {
            throw new IllegalStateException("Admission cycle cannot be opened outside its configured date range.");
        }
        status = AdmissionCycleStatus.OPEN;
    }

    public void closeApplications() {
        if (status != AdmissionCycleStatus.OPEN) {
            throw new IllegalStateException("Only an open admission cycle can be closed for applications.");
        }
        status = AdmissionCycleStatus.CLOSED;
    }

    public void beginSelection() {
        if (status != AdmissionCycleStatus.CLOSED) {
            throw new IllegalStateException("Admissions selection can only begin after applications close.");
        }
        status = AdmissionCycleStatus.SELECTION;
    }

    public void beginOffers() {
        if (status == AdmissionCycleStatus.OFFERS) {
            return;
        }
        if (status != AdmissionCycleStatus.SELECTION) {
            throw new IllegalStateException("Offer processing can only begin from selection.");
        }
        status = AdmissionCycleStatus.OFFERS;
    }

    public void complete() {
        if (status != AdmissionCycleStatus.OFFERS) {
            throw new IllegalStateException("Only an offer-stage admission cycle can be completed.");
        }
        status = AdmissionCycleStatus.COMPLETED;
    }

    public void archive() {
        if (status != AdmissionCycleStatus.COMPLETED) {
            throw new IllegalStateException("Only a completed admission cycle can be archived.");
        }
        status = AdmissionCycleStatus.ARCHIVED;
    }
}
