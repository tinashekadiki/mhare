package zw.ac.uz.emhare.coreidentity.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "workflow_instances")
@SQLRestriction("deleted_at IS NULL")
public class WorkflowInstance extends AuditableEntity {

    @Column(name = "workflow_code", nullable = false, length = 80)
    private String workflowCode;
    @Column(name = "subject_type", nullable = false, length = 80)
    private String subjectType;
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
    @Column(name = "subject_reference", nullable = false, length = 160)
    private String subjectReference;
    @Column(nullable = false, length = 240)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowStatus status;
    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId;
    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkflowInstance() {
    }

    public WorkflowInstance(
            String workflowCode,
            String subjectType,
            UUID subjectId,
            String subjectReference,
            String title,
            UUID initiatedByUserId,
            Instant initiatedAt) {
        this.workflowCode = requireText(workflowCode, "Workflow code").toUpperCase();
        this.subjectType = requireText(subjectType, "Subject type").toUpperCase();
        this.subjectId = java.util.Objects.requireNonNull(subjectId, "Subject id is required.");
        this.subjectReference = requireText(subjectReference, "Subject reference");
        this.title = requireText(title, "Workflow title");
        this.initiatedByUserId = java.util.Objects.requireNonNull(initiatedByUserId, "Initiating user is required.");
        this.initiatedAt = java.util.Objects.requireNonNull(initiatedAt, "Initiated timestamp is required.");
        this.status = WorkflowStatus.ACTIVE;
    }

    public void complete(Instant completedAt) {
        if (status != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Only an active workflow can be completed.");
        }
        this.status = WorkflowStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public String getWorkflowCode() { return workflowCode; }
    public String getSubjectType() { return subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public String getSubjectReference() { return subjectReference; }
    public String getTitle() { return title; }
    public WorkflowStatus getStatus() { return status; }
    public UUID getInitiatedByUserId() { return initiatedByUserId; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
