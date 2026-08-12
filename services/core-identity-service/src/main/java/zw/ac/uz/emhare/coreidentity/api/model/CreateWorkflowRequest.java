package zw.ac.uz.emhare.coreidentity.api.model;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;

/** @author Tinashe K */
public record CreateWorkflowRequest(
        @NotBlank @Size(max = 80) String workflowCode,
        @NotBlank @Size(max = 80) String subjectType,
        @NotNull UUID subjectId,
        @NotBlank @Size(max = 160) String subjectReference,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 240) String taskTitle,
        @NotBlank @Size(max = 2000) String taskDescription,
        UUID assignedUserId,
        UUID assignedRoleId,
        @NotNull WorkflowScopeType scopeType,
        UUID academicUnitId,
        Instant dueAt) {
}
