package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.CaptureMethod;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.MarkStatus;
import zw.ac.uz.emhare.assessmentresults.roster.AssessmentRosterEntry;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="student_assessment_marks") @SQLRestriction("deleted_at IS NULL")
public class StudentAssessmentMark extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assessment_component_id") private AssessmentComponent assessmentComponent;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assessment_roster_entry_id") private AssessmentRosterEntry rosterEntry;
    @Column(name="revision_number",nullable=false) private int revisionNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supersedes_mark_id") private StudentAssessmentMark supersedesMark;
    @Column(nullable=false,precision=8,scale=2) private BigDecimal score;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MarkStatus status;
    @Enumerated(EnumType.STRING) @Column(name="capture_method",nullable=false,length=20) private CaptureMethod captureMethod;
    @Column(name="captured_by_user_id",nullable=false) private UUID capturedByUserId; @Column(name="captured_at",nullable=false) private Instant capturedAt;
    @Column(name="submitted_by_user_id") private UUID submittedByUserId; @Column(name="submitted_at") private Instant submittedAt;
    protected StudentAssessmentMark() {}
    public StudentAssessmentMark(AssessmentComponent component,AssessmentRosterEntry entry,BigDecimal score,CaptureMethod method,UUID actor,Instant now){this(component,entry,1,null,score,method,actor,now);}
    private StudentAssessmentMark(AssessmentComponent component,AssessmentRosterEntry entry,int revision,StudentAssessmentMark supersedes,BigDecimal score,CaptureMethod method,UUID actor,Instant now){
        requireScore(component,score); assessmentComponent=component; rosterEntry=entry; revisionNumber=revision; supersedesMark=supersedes; this.score=score; captureMethod=method; capturedByUserId=actor; capturedAt=now; status=MarkStatus.CAPTURED;
    }
    public static StudentAssessmentMark amendment(StudentAssessmentMark original,BigDecimal score,UUID actor,Instant now){return new StudentAssessmentMark(original.getAssessmentComponent(),original.getRosterEntry(),original.getRevisionNumber()+1,original,score,CaptureMethod.AMENDMENT,actor,now);}
    public void reviseCapturedScore(BigDecimal newScore, UUID actor, Instant now, long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Mark was changed by another user. Refresh before retrying.");if(status!=MarkStatus.CAPTURED)throw new IllegalStateException("A submitted mark requires the amendment workflow.");requireScore(assessmentComponent,newScore);score=newScore;capturedByUserId=actor;capturedAt=now;}
    public void submit(UUID actor,Instant now,long expectedVersion){if(getVersion()!=expectedVersion)throw new IllegalStateException("Mark was changed by another user. Refresh before retrying."); if(status!=MarkStatus.CAPTURED)throw new IllegalStateException("Only a captured mark can be submitted."); status=MarkStatus.SUBMITTED; submittedByUserId=actor; submittedAt=now;}
    public void supersede(){if(status!=MarkStatus.SUBMITTED)throw new IllegalStateException("Only a submitted mark can be superseded.");status=MarkStatus.SUPERSEDED;}
    private static void requireScore(AssessmentComponent component,BigDecimal score){if(score==null||score.signum()<0||score.compareTo(component.getMaximumMark())>0)throw new IllegalArgumentException("Score must be between zero and the component maximum mark.");}
    public AssessmentComponent getAssessmentComponent(){return assessmentComponent;} public AssessmentRosterEntry getRosterEntry(){return rosterEntry;} public int getRevisionNumber(){return revisionNumber;} public UUID getSupersedesMarkId(){return supersedesMark==null?null:supersedesMark.getId();} public BigDecimal getScore(){return score;} public MarkStatus getStatus(){return status;} public CaptureMethod getCaptureMethod(){return captureMethod;} public UUID getCapturedByUserId(){return capturedByUserId;} public Instant getCapturedAt(){return capturedAt;} public UUID getSubmittedByUserId(){return submittedByUserId;} public Instant getSubmittedAt(){return submittedAt;}
}
