package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.List;

/** Read-only position of one application in the governed Admissions workflow. @author Tinashe K */
public record AdmissionsApplicationWorkflowProgress(
        String currentStageCode,
        List<WorkflowStage> stages) {

    public record WorkflowStage(
            int sequence,
            String code,
            String label,
            String state,
            String statusLabel,
            String detail,
            Instant occurredAt) {
    }
}
