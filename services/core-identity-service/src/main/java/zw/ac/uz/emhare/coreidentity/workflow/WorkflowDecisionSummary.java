package zw.ac.uz.emhare.coreidentity.workflow;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record WorkflowDecisionSummary(
        UUID id,
        String decisionCode,
        String comment,
        UUID actorUserId,
        String actorName,
        Instant decidedAt) {
    static WorkflowDecisionSummary from(WorkflowDecision decision) {
        return new WorkflowDecisionSummary(
                decision.getId(),
                decision.getDecisionCode(),
                decision.getDecisionComment(),
                decision.getActorUser().getId(),
                decision.getActorUser().getDisplayName(),
                decision.getDecidedAt());
    }
}
