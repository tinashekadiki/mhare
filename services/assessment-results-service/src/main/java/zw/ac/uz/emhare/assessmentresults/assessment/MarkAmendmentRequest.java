package zw.ac.uz.emhare.assessmentresults.assessment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentEnums.AmendmentStatus;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name="mark_amendment_requests") @SQLRestriction("deleted_at IS NULL")
public class MarkAmendmentRequest extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="original_mark_id") private StudentAssessmentMark originalMark;
    @Column(name="proposed_score",nullable=false,precision=8,scale=2) private BigDecimal proposedScore;
    @Column(nullable=false,length=1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AmendmentStatus status;
    @Column(name="requested_by_user_id",nullable=false) private UUID requestedByUserId; @Column(name="requested_at",nullable=false) private Instant requestedAt;
    @Column(name="decided_by_user_id") private UUID decidedByUserId; @Column(name="decided_at") private Instant decidedAt;
    @Column(name="decision_reason",length=1000) private String decisionReason;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="replacement_mark_id") private StudentAssessmentMark replacementMark;
    protected MarkAmendmentRequest() {}
    public MarkAmendmentRequest(StudentAssessmentMark mark,BigDecimal score,String reason,UUID actor,Instant now){
        if(mark.getStatus()!=AssessmentEnums.MarkStatus.SUBMITTED)throw new IllegalStateException("Only a submitted mark can enter amendment workflow.");
        if(score==null||score.signum()<0||score.compareTo(mark.getAssessmentComponent().getMaximumMark())>0)throw new IllegalArgumentException("Proposed score must be between zero and the component maximum mark.");
        originalMark=mark; proposedScore=score; this.reason=AssessmentScheme.requireText(reason,"Amendment reason"); requestedByUserId=actor; requestedAt=now; status=AmendmentStatus.REQUESTED;
    }
    public StudentAssessmentMark prepareApprovedReplacement(long expectedVersion, UUID actor, Instant now){requirePending(expectedVersion);StudentAssessmentMark replacement=StudentAssessmentMark.amendment(originalMark,proposedScore,actor,now);replacement.submit(actor,now,replacement.getVersion());originalMark.supersede();return replacement;}
    public void approveWithReplacement(StudentAssessmentMark replacement,UUID actor,String reason,Instant now){if(status!=AmendmentStatus.REQUESTED||replacement==null)throw new IllegalStateException("A requested amendment and replacement mark are required.");status=AmendmentStatus.APPROVED;decidedByUserId=actor;decidedAt=now;decisionReason=AssessmentScheme.requireText(reason,"Decision reason");replacementMark=replacement;}
    public void reject(UUID actor,String reason,Instant now,long expectedVersion){requirePending(expectedVersion);status=AmendmentStatus.REJECTED;decidedByUserId=actor;decidedAt=now;decisionReason=AssessmentScheme.requireText(reason,"Decision reason");}
    private void requirePending(long expected){if(getVersion()!=expected)throw new IllegalStateException("Amendment request was changed by another user. Refresh before retrying.");if(status!=AmendmentStatus.REQUESTED)throw new IllegalStateException("Only a requested amendment can be decided.");}
    public StudentAssessmentMark getOriginalMark(){return originalMark;} public BigDecimal getProposedScore(){return proposedScore;} public String getReason(){return reason;} public AmendmentStatus getStatus(){return status;} public UUID getRequestedByUserId(){return requestedByUserId;} public Instant getRequestedAt(){return requestedAt;} public UUID getDecidedByUserId(){return decidedByUserId;} public Instant getDecidedAt(){return decidedAt;} public String getDecisionReason(){return decisionReason;} public StudentAssessmentMark getReplacementMark(){return replacementMark;}
}
