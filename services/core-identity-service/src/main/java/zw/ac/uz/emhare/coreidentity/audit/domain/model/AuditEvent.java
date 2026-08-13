package zw.ac.uz.emhare.coreidentity.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable business-level audit event for Core operations. @author Tinashe K */
@Audited
@Entity
@Table(name = "audit_events")
public class AuditEvent extends AuditableEntity {

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "subject_type", nullable = false, length = 100)
    private String subjectType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false, length = 1000)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private String afterJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            UUID actorUserId,
            String eventType,
            String subjectType,
            UUID subjectId,
            String summary,
            String beforeJson,
            String afterJson,
            Instant occurredAt) {
        this.actorUserId = actorUserId;
        this.eventType = requireText(eventType, "Event type");
        this.subjectType = requireText(subjectType, "Subject type");
        this.subjectId = subjectId;
        this.summary = requireText(summary, "Audit summary");
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.occurredAt = occurredAt;
    }

    public UUID getActorUserId() { return actorUserId; }
    public String getEventType() { return eventType; }
    public String getSubjectType() { return subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public String getSummary() { return summary; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public Instant getOccurredAt() { return occurredAt; }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
