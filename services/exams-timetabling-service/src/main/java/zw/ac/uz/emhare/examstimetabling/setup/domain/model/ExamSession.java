package zw.ac.uz.emhare.examstimetabling.setup.domain.model;

import zw.ac.uz.emhare.examstimetabling.setup.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_sessions") @SQLRestriction("deleted_at IS NULL")
public class ExamSession extends AuditableEntity {
    public enum AssessmentType { FINAL_EXAM,SUPPLEMENTARY,DEFERRED,SPECIAL }
    public enum Status { DRAFT,APPROVED,CLOSED }
    @Column(name="academic_period_id",nullable=false) private UUID academicPeriodId;
    @Column(name="academic_period_code",nullable=false,length=50) private String academicPeriodCode;
    @Column(nullable=false,length=40) private String code; @Column(nullable=false,length=150) private String name;
    @Enumerated(EnumType.STRING) @Column(name="assessment_type",nullable=false,length=30) private AssessmentType assessmentType;
    @Column(name="starts_on",nullable=false) private LocalDate startsOn; @Column(name="ends_on",nullable=false) private LocalDate endsOn;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="approved_by_user_id") private UUID approvedByUserId; @Column(name="approved_at") private Instant approvedAt;
    @Column(name="approval_reason",length=1000) private String approvalReason;
    protected ExamSession() {}
    public ExamSession(UUID academicPeriodId,String academicPeriodCode,String code,String name,AssessmentType assessmentType,LocalDate startsOn,LocalDate endsOn){
        if(academicPeriodId==null||assessmentType==null||startsOn==null||endsOn==null||endsOn.isBefore(startsOn))throw new IllegalArgumentException("Exam session scope and date range are invalid.");
        this.academicPeriodId=academicPeriodId;this.academicPeriodCode=ExamVenueType.text(academicPeriodCode,"Academic period code");
        this.code=ExamVenueType.text(code,"Exam session code").toUpperCase();this.name=ExamVenueType.text(name,"Exam session name");
        this.assessmentType=assessmentType;this.startsOn=startsOn;this.endsOn=endsOn;status=Status.DRAFT;
    }
    public void approve(UUID actor,String reason,Instant now,long expectedVersion){requireVersion(expectedVersion);if(status!=Status.DRAFT)throw new IllegalStateException("Only a draft exam session can be approved.");approvedByUserId=actor;approvalReason=ExamVenueType.text(reason,"Approval reason");approvedAt=now;status=Status.APPROVED;}
    private void requireVersion(long expected){if(getVersion()!=expected)throw new IllegalStateException("Exam session was changed by another user. Refresh before retrying.");}
    public UUID getAcademicPeriodId(){return academicPeriodId;} public String getAcademicPeriodCode(){return academicPeriodCode;}
    public String getCode(){return code;} public String getName(){return name;} public AssessmentType getAssessmentType(){return assessmentType;}
    public LocalDate getStartsOn(){return startsOn;} public LocalDate getEndsOn(){return endsOn;} public Status getStatus(){return status;}
    public UUID getApprovedByUserId(){return approvedByUserId;} public Instant getApprovedAt(){return approvedAt;} public String getApprovalReason(){return approvalReason;}
}
