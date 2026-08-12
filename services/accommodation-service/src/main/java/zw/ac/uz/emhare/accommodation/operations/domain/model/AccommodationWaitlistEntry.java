package zw.ac.uz.emhare.accommodation.operations.domain.model;

import zw.ac.uz.emhare.accommodation.operations.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_waitlist_entries")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationWaitlistEntry extends AuditableEntity {
    public enum Status { ACTIVE, ALLOCATED, WITHDRAWN, REMOVED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_application_id")
    private AccommodationApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_period_id")
    private AccommodationApplicationPeriod applicationPeriod;
    @Column(name = "waitlist_position", nullable = false) private int waitlistPosition;
    @Column(name = "priority_score", nullable = false) private int priorityScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "entered_by_user_id", nullable = false) private UUID enteredByUserId;
    @Column(name = "entered_at", nullable = false) private Instant enteredAt;
    @Column(name = "removed_by_user_id") private UUID removedByUserId;
    @Column(name = "removed_at") private Instant removedAt;
    @Column(name = "removal_reason", length = 1000) private String removalReason;

    protected AccommodationWaitlistEntry() {}

    public AccommodationWaitlistEntry(AccommodationApplication application, int waitlistPosition,
            UUID actorUserId, Instant occurredAt) {
        if (application == null || application.getStatus() != AccommodationApplication.Status.WAITLISTED) {
            throw new IllegalArgumentException("A waitlisted accommodation application is required.");
        }
        if (waitlistPosition < 1) throw new IllegalArgumentException("Wait-list position must be positive.");
        if (actorUserId == null || occurredAt == null) throw new IllegalArgumentException("Entering operator and time are required.");
        this.application = application;
        this.applicationPeriod = application.getApplicationPeriod();
        this.waitlistPosition = waitlistPosition;
        this.priorityScore = application.getPriorityScore();
        this.status = Status.ACTIVE;
        this.enteredByUserId = actorUserId;
        this.enteredAt = occurredAt;
    }

    public void remove(Status targetStatus, UUID actorUserId, String reason, Instant occurredAt) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Only an active wait-list entry can be removed.");
        if (targetStatus == Status.ACTIVE) throw new IllegalArgumentException("A removal outcome is required.");
        removedByUserId = actorUserId;
        removedAt = occurredAt;
        removalReason = AccommodationValueRules.required(reason, "Wait-list removal reason");
        status = targetStatus;
    }

    public AccommodationApplication getApplication() { return application; }
    public AccommodationApplicationPeriod getApplicationPeriod() { return applicationPeriod; }
    public int getWaitlistPosition() { return waitlistPosition; }
    public int getPriorityScore() { return priorityScore; }
    public Status getStatus() { return status; }
    public UUID getEnteredByUserId() { return enteredByUserId; }
    public Instant getEnteredAt() { return enteredAt; }
    public UUID getRemovedByUserId() { return removedByUserId; }
    public Instant getRemovedAt() { return removedAt; }
    public String getRemovalReason() { return removalReason; }
}
