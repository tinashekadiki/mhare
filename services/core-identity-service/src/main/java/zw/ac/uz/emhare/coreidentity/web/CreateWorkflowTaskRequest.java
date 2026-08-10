package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowScopeType;

/** @author Tinashe K */
public record CreateWorkflowTaskRequest(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 2000) String description,
        UUID assignedUserId,
        UUID assignedRoleId,
        @NotNull WorkflowScopeType scopeType,
        UUID academicUnitId,
        Instant dueAt) {
}
