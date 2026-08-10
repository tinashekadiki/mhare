package zw.ac.uz.emhare.examstimetabling.roster;

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
@Audited @Entity @Table(name="exam_registration_imports") @SQLRestriction("deleted_at IS NULL")
public class ExamRegistrationImport extends AuditableEntity {
    @Column(name="source_event_id",nullable=false) private UUID sourceEventId;
    @Column(name="registration_session_id",nullable=false) private UUID registrationSessionId;
    @Column(name="student_id",nullable=false) private UUID studentId;
    @Column(name="student_number",nullable=false,length=40) private String studentNumber;
    @Column(name="programme_enrolment_id",nullable=false) private UUID programmeEnrolmentId;
    @Column(name="programme_id",nullable=false) private UUID programmeId;
    @Column(name="programme_version_id",nullable=false) private UUID programmeVersionId;
    @Column(name="academic_period_id",nullable=false) private UUID academicPeriodId;
    @Column(name="academic_period_code",nullable=false,length=50) private String academicPeriodCode;
    @Column(name="academic_period_name",nullable=false,length=150) private String academicPeriodName;
    @Column(name="academic_period_starts_on",nullable=false) private LocalDate academicPeriodStartsOn;
    @Column(name="academic_period_ends_on",nullable=false) private LocalDate academicPeriodEndsOn;
    @Column(name="imported_at",nullable=false) private Instant importedAt;

    protected ExamRegistrationImport() {}
    public ExamRegistrationImport(StudentRegistrationConfirmedEvent event, Instant importedAt) {
        sourceEventId=event.eventId(); registrationSessionId=event.registrationSessionId(); studentId=event.studentId();
        studentNumber=event.studentNumber().trim(); programmeEnrolmentId=event.programmeEnrolmentId();
        programmeId=event.programmeId(); programmeVersionId=event.programmeVersionId(); academicPeriodId=event.academicPeriodId();
        academicPeriodCode=event.academicPeriodCode().trim(); academicPeriodName=event.academicPeriodName().trim();
        academicPeriodStartsOn=event.academicPeriodStartsOn(); academicPeriodEndsOn=event.academicPeriodEndsOn();
        this.importedAt=importedAt;
    }
    public boolean isExactReplay(StudentRegistrationConfirmedEvent event) {
        return sourceEventId.equals(event.eventId()) && registrationSessionId.equals(event.registrationSessionId())
                && studentId.equals(event.studentId()) && programmeEnrolmentId.equals(event.programmeEnrolmentId())
                && programmeVersionId.equals(event.programmeVersionId()) && academicPeriodId.equals(event.academicPeriodId())
                && event.modules()!=null;
    }
    public UUID getStudentId(){return studentId;} public String getStudentNumber(){return studentNumber;}
    public UUID getAcademicPeriodId(){return academicPeriodId;} public String getAcademicPeriodCode(){return academicPeriodCode;}
    public UUID getRegistrationSessionId(){return registrationSessionId;} public Instant getImportedAt(){return importedAt;}
}
