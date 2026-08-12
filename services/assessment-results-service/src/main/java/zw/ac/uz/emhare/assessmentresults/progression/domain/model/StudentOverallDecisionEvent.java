package zw.ac.uz.emhare.assessmentresults.progression.domain.model;

import zw.ac.uz.emhare.assessmentresults.progression.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_overall_decision_events")
@SQLRestriction("deleted_at IS NULL")
public class StudentOverallDecisionEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_overall_decision_id", nullable = false)
    private StudentOverallDecision decision;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private StudentOverallDecision.Status fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private StudentOverallDecision.Status toStatus;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StudentOverallDecisionEvent() {
    }

    public StudentOverallDecisionEvent(
            StudentOverallDecision decision,
            StudentOverallDecision.Status fromStatus,
            String reason,
            UUID actorUserId,
            Instant occurredAt) {
        this.decision = decision;
        this.fromStatus = fromStatus;
        this.toStatus = decision.getStatus();
        this.reason = ProgressionRuleSet.required(reason);
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }
}
