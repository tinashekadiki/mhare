package zw.ac.uz.emhare.accommodation.operations.domain.model;

import zw.ac.uz.emhare.accommodation.operations.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Immutable
@Entity
@Table(name = "room_allocation_events")
@SQLRestriction("deleted_at IS NULL")
public class RoomAllocationEvent extends AuditableEntity {
    public enum EventType { PROPOSED, APPROVED, CHECKED_IN, CHECKED_OUT, MOVED, WITHDRAWN, CANCELLED, BILLING_REQUESTED, BILLING_ACCEPTED, BILLING_FAILED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_allocation_id")
    private RoomAllocation allocation;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 30) private RoomAllocation.Status previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 30) private RoomAllocation.Status newStatus;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 30) private EventType eventType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_room_id") private AccommodationRoom fromRoom;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "to_room_id") private AccommodationRoom toRoom;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected RoomAllocationEvent() {}

    public RoomAllocationEvent(RoomAllocation allocation, RoomAllocation.Status previousStatus,
            RoomAllocation.Status newStatus, EventType eventType, AccommodationRoom fromRoom,
            AccommodationRoom toRoom, String reason, UUID actorUserId, Instant occurredAt) {
        if (allocation == null || newStatus == null || eventType == null || actorUserId == null || occurredAt == null) {
            throw new IllegalArgumentException("Allocation event identity, state, type, actor, and time are required.");
        }
        this.allocation = allocation;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.eventType = eventType;
        this.fromRoom = fromRoom;
        this.toRoom = toRoom;
        this.reason = AccommodationValueRules.required(reason, "Allocation event reason");
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public RoomAllocation getAllocation() { return allocation; }
    public RoomAllocation.Status getPreviousStatus() { return previousStatus; }
    public RoomAllocation.Status getNewStatus() { return newStatus; }
    public EventType getEventType() { return eventType; }
    public AccommodationRoom getFromRoom() { return fromRoom; }
    public AccommodationRoom getToRoom() { return toRoom; }
    public String getReason() { return reason; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getOccurredAt() { return occurredAt; }
}
