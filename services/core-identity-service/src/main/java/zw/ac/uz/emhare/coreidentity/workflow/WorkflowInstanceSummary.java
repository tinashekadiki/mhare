package zw.ac.uz.emhare.coreidentity.workflow;

import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record WorkflowInstanceSummary(
        UUID id,
        String workflowCode,
        String subjectType,
        UUID subjectId,
        String subjectReference,
        String title,
        WorkflowStatus status,
        UUID initiatedByUserId,
        Instant initiatedAt,
        Instant completedAt,
        long version,
        List<WorkflowTaskSummary> tasks) {
}
