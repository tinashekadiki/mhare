package zw.ac.uz.emhare.coreidentity.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record WorkflowTaskSummary(
        UUID id,
        UUID workflowInstanceId,
        String workflowCode,
        String subjectType,
        UUID subjectId,
        String subjectReference,
        String taskReference,
        String title,
        String description,
        WorkflowAssigneeType assigneeType,
        UUID assignedUserId,
        String assignedUserName,
        UUID assignedRoleId,
        String assignedRoleName,
        WorkflowScopeType scopeType,
        UUID academicUnitId,
        WorkflowTaskStatus status,
        Instant dueAt,
        UUID claimedByUserId,
        String claimedByUserName,
        Instant claimedAt,
        UUID completedByUserId,
        String completedByUserName,
        Instant completedAt,
        long version,
        List<WorkflowDecisionSummary> decisions) {

    static WorkflowTaskSummary from(WorkflowTask task, List<WorkflowDecision> decisions) {
        return new WorkflowTaskSummary(
                task.getId(),
                task.getWorkflowInstance().getId(),
                task.getWorkflowInstance().getWorkflowCode(),
                task.getWorkflowInstance().getSubjectType(),
                task.getWorkflowInstance().getSubjectId(),
                task.getWorkflowInstance().getSubjectReference(),
                task.getTaskReference(),
                task.getTitle(),
                task.getDescription(),
                task.getAssigneeType(),
                task.getAssignedUser() == null ? null : task.getAssignedUser().getId(),
                task.getAssignedUser() == null ? null : task.getAssignedUser().getDisplayName(),
                task.getAssignedRole() == null ? null : task.getAssignedRole().getId(),
                task.getAssignedRole() == null ? null : task.getAssignedRole().getName(),
                task.getScopeType(),
                task.getAcademicUnitId(),
                task.getStatus(),
                task.getDueAt(),
                task.getClaimedByUser() == null ? null : task.getClaimedByUser().getId(),
                task.getClaimedByUser() == null ? null : task.getClaimedByUser().getDisplayName(),
                task.getClaimedAt(),
                task.getCompletedByUser() == null ? null : task.getCompletedByUser().getId(),
                task.getCompletedByUser() == null ? null : task.getCompletedByUser().getDisplayName(),
                task.getCompletedAt(),
                task.getVersion(),
                decisions.stream().map(WorkflowDecisionSummary::from).toList());
    }
}
