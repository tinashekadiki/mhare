package zw.ac.uz.emhare.coreidentity.workflow.domain.model;

import zw.ac.uz.emhare.coreidentity.workflow.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "workflow_decisions")
@SQLRestriction("deleted_at IS NULL")
public class WorkflowDecision extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_task_id", nullable = false)
    private WorkflowTask workflowTask;
    @Column(name = "decision_code", nullable = false, length = 50)
    private String decisionCode;
    @Column(name = "decision_comment", nullable = false, length = 2000)
    private String decisionComment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private PlatformUser actorUser;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected WorkflowDecision() {
    }

    public WorkflowDecision(
            WorkflowTask workflowTask,
            String decisionCode,
            String decisionComment,
            PlatformUser actorUser,
            Instant decidedAt) {
        this.workflowTask = java.util.Objects.requireNonNull(workflowTask, "Workflow task is required.");
        this.decisionCode = requireText(decisionCode, "Decision code").toUpperCase();
        this.decisionComment = requireText(decisionComment, "Decision comment");
        this.actorUser = java.util.Objects.requireNonNull(actorUser, "Decision actor is required.");
        this.decidedAt = java.util.Objects.requireNonNull(decidedAt, "Decision timestamp is required.");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public WorkflowTask getWorkflowTask() { return workflowTask; }
    public String getDecisionCode() { return decisionCode; }
    public String getDecisionComment() { return decisionComment; }
    public PlatformUser getActorUser() { return actorUser; }
    public Instant getDecidedAt() { return decidedAt; }
}
