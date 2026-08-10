package zw.ac.uz.emhare.studentrecords.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "registration_sessions")
@SQLRestriction("deleted_at IS NULL")
public class RegistrationSession extends AuditableEntity {

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_enrolment_id", nullable = false)
    private StudentProgrammeEnrolment programmeEnrolment;
    @Column(name = "academic_period_id", nullable = false)
    private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50)
    private String academicPeriodCode;
    @Column(name = "academic_period_name", nullable = false, length = 150)
    private String academicPeriodName;
    @Column(name = "academic_period_starts_on", nullable = false)
    private LocalDate academicPeriodStartsOn;
    @Column(name = "academic_period_ends_on", nullable = false)
    private LocalDate academicPeriodEndsOn;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "programme_period_number", nullable = false)
    private int programmePeriodNumber;
    @Column(name = "owning_academic_unit_id", nullable = false)
    private UUID owningAcademicUnitId;
    @Column(name = "owning_academic_unit_code", nullable = false, length = 80)
    private String owningAcademicUnitCode;
    @Column(name = "owning_academic_unit_name", nullable = false, length = 200)
    private String owningAcademicUnitName;
    @Column(name = "programme_level_id", nullable = false)
    private UUID programmeLevelId;
    @Column(name = "programme_level_code", nullable = false, length = 80)
    private String programmeLevelCode;
    @Column(name = "programme_level_name", nullable = false, length = 200)
    private String programmeLevelName;
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 20)
    private RegistrationType registrationType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegistrationStatus status;
    @Column(name = "status_reason", nullable = false, length = 1000)
    private String statusReason;
    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "academic_approved_by_user_id")
    private UUID academicApprovedByUserId;
    @Column(name = "academic_approved_at")
    private Instant academicApprovedAt;
    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    protected RegistrationSession() {
    }

    public RegistrationSession(
            String registrationNumber,
            StudentProfile student,
            StudentProgrammeEnrolment programmeEnrolment,
            RegistrationCatalogue catalogue,
            RegistrationType registrationType,
            Instant initiatedAt) {
        this.registrationNumber = requireText(registrationNumber, "Registration number");
        this.student = student;
        this.programmeEnrolment = programmeEnrolment;
        this.academicPeriodId = catalogue.academicPeriodId();
        this.academicPeriodCode = requireText(catalogue.academicPeriodCode(), "Academic period code");
        this.academicPeriodName = requireText(catalogue.academicPeriodName(), "Academic period name");
        this.academicPeriodStartsOn = catalogue.academicPeriodStartsOn();
        this.academicPeriodEndsOn = catalogue.academicPeriodEndsOn();
        this.programmeVersionId = catalogue.programmeVersionId();
        this.programmePeriodNumber = catalogue.periodNumber();
        this.owningAcademicUnitId = catalogue.owningAcademicUnitId();
        this.owningAcademicUnitCode = requireText(catalogue.owningAcademicUnitCode(), "Owning academic unit code");
        this.owningAcademicUnitName = requireText(catalogue.owningAcademicUnitName(), "Owning academic unit name");
        this.programmeLevelId = catalogue.programmeLevelId();
        this.programmeLevelCode = requireText(catalogue.programmeLevelCode(), "Programme level code");
        this.programmeLevelName = requireText(catalogue.programmeLevelName(), "Programme level name");
        this.registrationType = registrationType;
        this.status = RegistrationStatus.DRAFT;
        this.statusReason = "Registration initiated from the approved curriculum.";
        this.initiatedAt = initiatedAt;
    }

    public RegistrationStatus submit(String reason, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(RegistrationStatus.DRAFT, "Only a draft registration can be submitted.");
        RegistrationStatus previous = status;
        status = RegistrationStatus.SUBMITTED;
        statusReason = requireText(reason, "Submission reason");
        submittedAt = now;
        return previous;
    }

    public RegistrationStatus approveAcademically(UUID actorUserId, String reason, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(RegistrationStatus.SUBMITTED, "Only a submitted registration can receive academic approval.");
        RegistrationStatus previous = status;
        status = RegistrationStatus.ACADEMIC_APPROVED;
        statusReason = requireText(reason, "Academic approval reason");
        academicApprovedByUserId = actorUserId;
        academicApprovedAt = now;
        return previous;
    }

    public RegistrationStatus confirm(UUID actorUserId, String reason, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(RegistrationStatus.ACADEMIC_APPROVED, "Only an academically approved registration can be confirmed.");
        RegistrationStatus previous = status;
        status = RegistrationStatus.CONFIRMED;
        statusReason = requireText(reason, "Confirmation reason");
        confirmedByUserId = actorUserId;
        confirmedAt = now;
        return previous;
    }

    public RegistrationStatus reject(UUID actorUserId, String reason, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != RegistrationStatus.SUBMITTED && status != RegistrationStatus.ACADEMIC_APPROVED) {
            throw new IllegalStateException("Only a submitted or academically approved registration can be rejected.");
        }
        RegistrationStatus previous = status;
        status = RegistrationStatus.REJECTED;
        statusReason = requireText(reason, "Rejection reason");
        rejectedByUserId = actorUserId;
        rejectedAt = now;
        return previous;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Registration was changed by another user. Refresh before retrying.");
        }
    }

    private void requireStatus(RegistrationStatus requiredStatus, String message) {
        if (status != requiredStatus) throw new IllegalStateException(message);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public StudentProfile getStudent() { return student; }
    public StudentProgrammeEnrolment getProgrammeEnrolment() { return programmeEnrolment; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getAcademicPeriodName() { return academicPeriodName; }
    public LocalDate getAcademicPeriodStartsOn() { return academicPeriodStartsOn; }
    public LocalDate getAcademicPeriodEndsOn() { return academicPeriodEndsOn; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
    public UUID getOwningAcademicUnitId() { return owningAcademicUnitId; }
    public String getOwningAcademicUnitCode() { return owningAcademicUnitCode; }
    public String getOwningAcademicUnitName() { return owningAcademicUnitName; }
    public UUID getProgrammeLevelId() { return programmeLevelId; }
    public String getProgrammeLevelCode() { return programmeLevelCode; }
    public String getProgrammeLevelName() { return programmeLevelName; }
    public RegistrationType getRegistrationType() { return registrationType; }
    public RegistrationStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getAcademicApprovedByUserId() { return academicApprovedByUserId; }
    public Instant getAcademicApprovedAt() { return academicApprovedAt; }
    public UUID getConfirmedByUserId() { return confirmedByUserId; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
