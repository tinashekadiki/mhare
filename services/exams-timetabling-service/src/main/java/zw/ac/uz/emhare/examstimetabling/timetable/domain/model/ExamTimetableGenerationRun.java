package zw.ac.uz.emhare.examstimetabling.timetable.domain.model;

import zw.ac.uz.emhare.examstimetabling.timetable.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSession;

/** @author Tinashe K */
@Audited @Entity @Table(name="exam_timetable_generation_runs") @SQLRestriction("deleted_at IS NULL")
public class ExamTimetableGenerationRun extends AuditableEntity {
    public enum Status { GENERATED,REVIEWED,APPROVED,PUBLISHED,REJECTED }
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="exam_session_id") private ExamSession examSession;
    @Column(name="run_number",nullable=false,length=60) private String runNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="candidate_count",nullable=false) private int candidateCount; @Column(name="module_count",nullable=false) private int moduleCount;
    @Column(name="timetable_entry_count",nullable=false) private int timetableEntryCount; @Column(name="conflict_count",nullable=false) private int conflictCount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="generation_policy",nullable=false,columnDefinition="jsonb") private Map<String,Object> generationPolicy;
    @Column(name="generated_by_user_id",nullable=false) private UUID generatedByUserId; @Column(name="generated_at",nullable=false) private Instant generatedAt;
    @Column(name="reviewed_by_user_id") private UUID reviewedByUserId; @Column(name="reviewed_at") private Instant reviewedAt; @Column(name="review_reason",length=1000) private String reviewReason;
    @Column(name="approved_by_user_id") private UUID approvedByUserId; @Column(name="approved_at") private Instant approvedAt; @Column(name="approval_reason",length=1000) private String approvalReason;
    @Column(name="published_by_user_id") private UUID publishedByUserId; @Column(name="published_at") private Instant publishedAt; @Column(name="publication_reason",length=1000) private String publicationReason;
    @Column(name="rejected_by_user_id") private UUID rejectedByUserId; @Column(name="rejected_at") private Instant rejectedAt; @Column(name="rejection_reason",length=1000) private String rejectionReason;
    protected ExamTimetableGenerationRun() {}
    public ExamTimetableGenerationRun(ExamSession session,String runNumber,int candidateCount,int moduleCount,int entryCount,
            int conflictCount,Map<String,Object> generationPolicy,UUID actor,Instant now){this.examSession=session;this.runNumber=runNumber;
        this.candidateCount=candidateCount;this.moduleCount=moduleCount;this.timetableEntryCount=entryCount;this.conflictCount=conflictCount;
        this.generationPolicy=generationPolicy;generatedByUserId=actor;generatedAt=now;status=Status.GENERATED;}
    public Status review(UUID actor,String reason,Instant now,long expected){version(expected);requireStatus(Status.GENERATED);distinct(actor,generatedByUserId,"The reviewer must be different from the timetable generator.");Status previous=status;reviewedByUserId=actor;reviewedAt=now;reviewReason=text(reason);status=Status.REVIEWED;return previous;}
    public Status approve(UUID actor,String reason,Instant now,long expected){version(expected);requireStatus(Status.REVIEWED);distinct(actor,generatedByUserId,"The approver must be different from the timetable generator.");distinct(actor,reviewedByUserId,"The approver must be different from the reviewer.");if(conflictCount!=0)throw new IllegalStateException("A timetable with conflicts cannot be approved.");Status previous=status;approvedByUserId=actor;approvedAt=now;approvalReason=text(reason);status=Status.APPROVED;return previous;}
    public Status publish(UUID actor,String reason,Instant now,long expected){version(expected);requireStatus(Status.APPROVED);distinct(actor,generatedByUserId,"The publisher must be independent from the generator.");distinct(actor,reviewedByUserId,"The publisher must be independent from the reviewer.");distinct(actor,approvedByUserId,"The publisher must be independent from the approver.");Status previous=status;publishedByUserId=actor;publishedAt=now;publicationReason=text(reason);status=Status.PUBLISHED;return previous;}
    public Status reject(UUID actor,String reason,Instant now,long expected){version(expected);if(status==Status.PUBLISHED||status==Status.REJECTED)throw new IllegalStateException("Published or rejected timetables cannot be rejected again.");Status previous=status;rejectedByUserId=actor;rejectedAt=now;rejectionReason=text(reason);status=Status.REJECTED;return previous;}
    private void requireStatus(Status required){if(status!=required)throw new IllegalStateException("Exam timetable must be "+required+" for this action.");}
    private void version(long expected){if(getVersion()!=expected)throw new IllegalStateException("Exam timetable was changed by another user. Refresh before retrying.");}
    private static void distinct(UUID actor,UUID prior,String message){if(actor.equals(prior))throw new IllegalStateException(message);}
    private static String text(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Workflow reason is required.");return value.trim();}
    public ExamSession getExamSession(){return examSession;} public String getRunNumber(){return runNumber;} public Status getStatus(){return status;}
    public int getCandidateCount(){return candidateCount;} public int getModuleCount(){return moduleCount;} public int getTimetableEntryCount(){return timetableEntryCount;}
    public int getConflictCount(){return conflictCount;} public Map<String,Object> getGenerationPolicy(){return generationPolicy;} public UUID getGeneratedByUserId(){return generatedByUserId;}
    public Instant getGeneratedAt(){return generatedAt;} public UUID getReviewedByUserId(){return reviewedByUserId;} public UUID getApprovedByUserId(){return approvedByUserId;}
    public UUID getPublishedByUserId(){return publishedByUserId;} public Instant getPublishedAt(){return publishedAt;}
}
