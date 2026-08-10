package zw.ac.uz.emhare.examstimetabling.invigilation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.timetable.ExamStudentTimetableEntry;

/** Immutable original exam-room incident with segregated review and resolution. @author Tinashe K */
@Audited @Entity @Table(name="exam_incident_reports") @SQLRestriction("deleted_at IS NULL")
public class ExamIncidentReport extends AuditableEntity {
    public enum Type { LATE_ARRIVAL,SUSPECTED_MISCONDUCT,MEDICAL,EVACUATION,DISRUPTION,OTHER }
    public enum Severity { LOW,MEDIUM,HIGH,CRITICAL }
    public enum Status { REPORTED,REVIEWED,RESOLVED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="attendance_session_id") private ExamAttendanceSession attendanceSession;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_timetable_entry_id") private ExamStudentTimetableEntry studentTimetableEntry;
    @Column(name="incident_number",nullable=false,length=70) private String incidentNumber;
    @Enumerated(EnumType.STRING) @Column(name="incident_type",nullable=false,length=30) private Type incidentType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Severity severity;
    @Column(nullable=false,length=2000) private String description;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="reported_by_user_id",nullable=false) private UUID reportedByUserId;
    @Column(name="reported_at",nullable=false) private Instant reportedAt;
    @Column(name="reviewed_by_user_id") private UUID reviewedByUserId;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(name="review_reason",length=1000) private String reviewReason;
    @Column(name="resolved_by_user_id") private UUID resolvedByUserId;
    @Column(name="resolved_at") private Instant resolvedAt;
    @Column(length=2000) private String resolution;

    protected ExamIncidentReport() {}
    public ExamIncidentReport(ExamAttendanceSession attendanceSession,ExamStudentTimetableEntry studentTimetableEntry,
            String incidentNumber,Type incidentType,Severity severity,String description,Instant occurredAt,UUID reporter,Instant reportedAt) {
        this.attendanceSession=attendanceSession;this.studentTimetableEntry=studentTimetableEntry;
        this.incidentNumber=ExamAttendanceSession.required(incidentNumber,"Incident number");this.incidentType=incidentType;this.severity=severity;
        this.description=ExamAttendanceSession.required(description,"Incident description");this.occurredAt=occurredAt;
        reportedByUserId=reporter;this.reportedAt=reportedAt;status=Status.REPORTED;
    }
    public void review(UUID reviewer,Instant now,String reason,long expectedVersion) {
        requireVersion(expectedVersion);if(status!=Status.REPORTED)throw new IllegalStateException("Only a reported incident can be reviewed.");
        distinct(reviewer,reportedByUserId,"The incident reviewer must be different from the reporter.");
        reviewedByUserId=reviewer;reviewedAt=now;reviewReason=ExamAttendanceSession.required(reason,"Review reason");status=Status.REVIEWED;
    }
    public void resolve(UUID resolver,Instant now,String resolution,long expectedVersion) {
        requireVersion(expectedVersion);if(status!=Status.REVIEWED)throw new IllegalStateException("Incident review is required before resolution.");
        distinct(resolver,reportedByUserId,"The incident resolver must be different from the reporter.");
        distinct(resolver,reviewedByUserId,"The incident resolver must be different from the reviewer.");
        resolvedByUserId=resolver;resolvedAt=now;this.resolution=ExamAttendanceSession.required(resolution,"Resolution");status=Status.RESOLVED;
    }
    private void requireVersion(long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Incident report was changed by another user. Refresh before retrying.");}
    private static void distinct(UUID actor,UUID prior,String message){if(actor.equals(prior))throw new IllegalStateException(message);}
    public ExamAttendanceSession getAttendanceSession(){return attendanceSession;} public ExamStudentTimetableEntry getStudentTimetableEntry(){return studentTimetableEntry;}
    public String getIncidentNumber(){return incidentNumber;} public Type getIncidentType(){return incidentType;} public Severity getSeverity(){return severity;}
    public String getDescription(){return description;} public Instant getOccurredAt(){return occurredAt;} public Status getStatus(){return status;}
    public UUID getReportedByUserId(){return reportedByUserId;} public Instant getReportedAt(){return reportedAt;}
    public UUID getReviewedByUserId(){return reviewedByUserId;} public Instant getReviewedAt(){return reviewedAt;} public String getReviewReason(){return reviewReason;}
    public UUID getResolvedByUserId(){return resolvedByUserId;} public Instant getResolvedAt(){return resolvedAt;} public String getResolution(){return resolution;}
}
