package zw.ac.uz.emhare.communications.content.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.communications.content.domain.model.CommunicationValues.WorkflowStatus;

/** Append-only editorial workflow evidence. @author Tinashe K */
@Audited
@Entity
@Table(name = "communication_workflow_events")
@SQLRestriction("deleted_at IS NULL")
public class CommunicationWorkflowEvent extends AuditableEntity {

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "version_id", nullable = false)
  private UUID versionId;

  @Column(name = "event_type", nullable = false, length = 40)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private WorkflowStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private WorkflowStatus toStatus;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(length = 1000)
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected CommunicationWorkflowEvent() {}

  public CommunicationWorkflowEvent(
      UUID itemId,
      UUID versionId,
      String eventType,
      WorkflowStatus fromStatus,
      WorkflowStatus toStatus,
      UUID actorUserId,
      String reason,
      Instant occurredAt) {
    this.itemId = itemId;
    this.versionId = versionId;
    this.eventType = eventType;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.actorUserId = actorUserId;
    this.reason = reason;
    this.occurredAt = occurredAt;
  }

  public UUID getItemId() {
    return itemId;
  }

  public UUID getVersionId() {
    return versionId;
  }

  public String getEventType() {
    return eventType;
  }

  public WorkflowStatus getFromStatus() {
    return fromStatus;
  }

  public WorkflowStatus getToStatus() {
    return toStatus;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getReason() {
    return reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
