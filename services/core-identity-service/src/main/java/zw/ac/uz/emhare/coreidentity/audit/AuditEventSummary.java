package zw.ac.uz.emhare.coreidentity.audit;

import java.time.Instant;
import java.util.UUID;
import zw.ac.uz.emhare.coreidentity.audit.domain.model.AuditEvent;

/** @author Tinashe K */
public record AuditEventSummary(
        UUID id,
        UUID actorUserId,
        String eventType,
        String subjectType,
        UUID subjectId,
        String summary,
        String beforeJson,
        String afterJson,
        Instant occurredAt) {

    static AuditEventSummary from(AuditEvent event) {
        return new AuditEventSummary(
                event.getId(), event.getActorUserId(), event.getEventType(), event.getSubjectType(),
                event.getSubjectId(), event.getSummary(), event.getBeforeJson(), event.getAfterJson(),
                event.getOccurredAt());
    }
}
