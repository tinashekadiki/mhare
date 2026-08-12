package zw.ac.uz.emhare.coreidentity.workflow.application.command;

import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;

import zw.ac.uz.emhare.coreidentity.workflow.*;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record CreateWorkflowCommand(
        String workflowCode,
        String subjectType,
        UUID subjectId,
        String subjectReference,
        String title,
        String taskTitle,
        String taskDescription,
        UUID assignedUserId,
        UUID assignedRoleId,
        WorkflowScopeType scopeType,
        UUID academicUnitId,
        Instant dueAt) {
}
