package zw.ac.uz.emhare.coreidentity.workflow.domain.model;

import zw.ac.uz.emhare.coreidentity.workflow.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "workflow_tasks")
@SQLRestriction("deleted_at IS NULL")
public class WorkflowTask extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;
    @Column(name = "task_reference", nullable = false, length = 50)
    private String taskReference;
    @Column(nullable = false, length = 240)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", nullable = false, length = 20)
    private WorkflowAssigneeType assigneeType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private PlatformUser assignedUser;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_role_id")
    private Role assignedRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private WorkflowScopeType scopeType;
    @Column(name = "academic_unit_id")
    private UUID academicUnitId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowTaskStatus status;
    @Column(name = "due_at")
    private Instant dueAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by_user_id")
    private PlatformUser claimedByUser;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private PlatformUser completedByUser;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected WorkflowTask() {
    }

    public WorkflowTask(
            WorkflowInstance workflowInstance,
            String taskReference,
            String title,
            String description,
            PlatformUser assignedUser,
            Role assignedRole,
            WorkflowScopeType scopeType,
            UUID academicUnitId,
            Instant dueAt) {
        this.workflowInstance = Objects.requireNonNull(workflowInstance, "Workflow instance is required.");
        this.taskReference = requireText(taskReference, "Task reference");
        this.title = requireText(title, "Task title");
        this.description = requireText(description, "Task description");
        if ((assignedUser == null) == (assignedRole == null)) {
            throw new IllegalArgumentException("Assign a workflow task to exactly one user or role.");
        }
        this.assignedUser = assignedUser;
        this.assignedRole = assignedRole;
        this.assigneeType = assignedUser == null ? WorkflowAssigneeType.ROLE : WorkflowAssigneeType.USER;
        this.scopeType = Objects.requireNonNull(scopeType, "Workflow scope is required.");
        if (scopeType == WorkflowScopeType.ACADEMIC_UNIT && academicUnitId == null) {
            throw new IllegalArgumentException("Academic-unit workflow scope requires an academic unit.");
        }
        if (scopeType == WorkflowScopeType.INSTITUTION && academicUnitId != null) {
            throw new IllegalArgumentException("Institution workflow scope cannot carry an academic unit.");
        }
        this.academicUnitId = academicUnitId;
        this.dueAt = dueAt;
        this.status = WorkflowTaskStatus.OPEN;
    }

    public void claim(PlatformUser user, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status == WorkflowTaskStatus.CLAIMED && claimedByUser.getId().equals(user.getId())) return;
        if (status != WorkflowTaskStatus.OPEN) {
            throw new IllegalStateException("Only an open workflow task can be claimed.");
        }
        status = WorkflowTaskStatus.CLAIMED;
        claimedByUser = user;
        claimedAt = now;
    }

    public void complete(PlatformUser user, Instant now, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != WorkflowTaskStatus.CLAIMED) {
            throw new IllegalStateException("Claim the workflow task before recording a decision.");
        }
        if (!claimedByUser.getId().equals(user.getId())) {
            throw new IllegalStateException("Only the operator who claimed the task can record its decision.");
        }
        status = WorkflowTaskStatus.COMPLETED;
        completedByUser = user;
        completedAt = now;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Workflow task changed after it was loaded. Refresh before retrying.");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public String getTaskReference() { return taskReference; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public WorkflowAssigneeType getAssigneeType() { return assigneeType; }
    public PlatformUser getAssignedUser() { return assignedUser; }
    public Role getAssignedRole() { return assignedRole; }
    public WorkflowScopeType getScopeType() { return scopeType; }
    public UUID getAcademicUnitId() { return academicUnitId; }
    public WorkflowTaskStatus getStatus() { return status; }
    public Instant getDueAt() { return dueAt; }
    public PlatformUser getClaimedByUser() { return claimedByUser; }
    public Instant getClaimedAt() { return claimedAt; }
    public PlatformUser getCompletedByUser() { return completedByUser; }
    public Instant getCompletedAt() { return completedAt; }
}
