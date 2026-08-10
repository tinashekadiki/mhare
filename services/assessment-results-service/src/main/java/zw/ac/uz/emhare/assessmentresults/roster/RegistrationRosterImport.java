package zw.ac.uz.emhare.assessmentresults.roster;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "registration_roster_imports")
@SQLRestriction("deleted_at IS NULL")
public class RegistrationRosterImport extends AuditableEntity {

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;
    @Column(name = "registration_session_id", nullable = false)
    private UUID registrationSessionId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40)
    private String studentNumber;
    @Column(name = "programme_enrolment_id", nullable = false)
    private UUID programmeEnrolmentId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
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
    @Column(name = "programme_period_number", nullable = false)
    private int programmePeriodNumber;
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected RegistrationRosterImport() {
    }

    public RegistrationRosterImport(StudentRegistrationConfirmedEvent event, Instant importedAt) {
        this.sourceEventId = event.eventId();
        this.registrationSessionId = event.registrationSessionId();
        this.studentId = event.studentId();
        this.studentNumber = event.studentNumber().trim();
        this.programmeEnrolmentId = event.programmeEnrolmentId();
        this.programmeId = event.programmeId();
        this.programmeVersionId = event.programmeVersionId();
        this.academicPeriodId = event.academicPeriodId();
        this.academicPeriodCode = event.academicPeriodCode().trim();
        this.academicPeriodName = event.academicPeriodName().trim();
        this.academicPeriodStartsOn = event.academicPeriodStartsOn();
        this.academicPeriodEndsOn = event.academicPeriodEndsOn();
        this.programmePeriodNumber = event.programmePeriodNumber();
        this.importedAt = importedAt;
    }

    public boolean isExactReplay(StudentRegistrationConfirmedEvent event) {
        return sourceEventId.equals(event.eventId())
                && registrationSessionId.equals(event.registrationSessionId())
                && studentId.equals(event.studentId())
                && programmeEnrolmentId.equals(event.programmeEnrolmentId())
                && programmeVersionId.equals(event.programmeVersionId())
                && academicPeriodId.equals(event.academicPeriodId());
    }

    public UUID getSourceEventId() { return sourceEventId; }
    public UUID getRegistrationSessionId() { return registrationSessionId; }
    public UUID getStudentId() { return studentId; }
    public String getStudentNumber() { return studentNumber; }
    public UUID getProgrammeEnrolmentId() { return programmeEnrolmentId; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getAcademicPeriodName() { return academicPeriodName; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
    public Instant getImportedAt() { return importedAt; }
}
