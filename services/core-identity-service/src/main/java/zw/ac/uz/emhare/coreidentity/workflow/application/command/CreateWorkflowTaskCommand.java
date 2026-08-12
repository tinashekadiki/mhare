package zw.ac.uz.emhare.coreidentity.workflow.application.command;

import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;

import zw.ac.uz.emhare.coreidentity.workflow.*;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record CreateWorkflowTaskCommand(
        String title,
        String description,
        UUID assignedUserId,
        UUID assignedRoleId,
        WorkflowScopeType scopeType,
        UUID academicUnitId,
        Instant dueAt) {
}
