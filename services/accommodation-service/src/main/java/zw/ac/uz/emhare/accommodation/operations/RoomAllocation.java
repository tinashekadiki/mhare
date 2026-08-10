package zw.ac.uz.emhare.accommodation.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.accommodation.setup.AccommodationRoom;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "room_allocations")
@SQLRestriction("deleted_at IS NULL")
public class RoomAllocation extends AuditableEntity {
    public enum Status { PROPOSED, ALLOCATED, CHECKED_IN, CHECKED_OUT, WITHDRAWN, CANCELLED }
    public enum BillingStatus { NOT_REQUESTED, PENDING, ACCEPTED, FAILED }

    @Column(name = "allocation_number", nullable = false, length = 60) private String allocationNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_application_id")
    private AccommodationApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private AccommodationRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_rate_id")
    private AccommodationRate accommodationRate;
    @Column(name = "occupancy_starts_on", nullable = false) private LocalDate occupancyStartsOn;
    @Column(name = "occupancy_ends_on", nullable = false) private LocalDate occupancyEndsOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(name = "allocated_by_user_id", nullable = false) private UUID allocatedByUserId;
    @Column(name = "allocated_at", nullable = false) private Instant allocatedAt;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000) private String approvalReason;
    @Column(name = "checked_in_by_user_id") private UUID checkedInByUserId;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "check_in_notes", length = 1000) private String checkInNotes;
    @Column(name = "checked_out_by_user_id") private UUID checkedOutByUserId;
    @Column(name = "checked_out_at") private Instant checkedOutAt;
    @Column(name = "check_out_notes", length = 1000) private String checkOutNotes;
    @Column(name = "ended_by_user_id") private UUID endedByUserId;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "end_reason", length = 1000) private String endReason;
    @Column(name = "billing_event_id") private UUID billingEventId;
    @Enumerated(EnumType.STRING) @Column(name = "billing_status", nullable = false, length = 20) private BillingStatus billingStatus;

    protected RoomAllocation() {}

    public RoomAllocation(String allocationNumber, AccommodationApplication application, AccommodationRoom room,
            AccommodationRate accommodationRate, LocalDate occupancyStartsOn, LocalDate occupancyEndsOn,
            UUID actorUserId, Instant occurredAt) {
        if (application == null || room == null || accommodationRate == null || occupancyStartsOn == null
                || occupancyEndsOn == null || actorUserId == null || occurredAt == null) {
            throw new IllegalArgumentException("Application, room, active rate, occupancy dates, and allocating operator are required.");
        }
        if (application.getStatus() != AccommodationApplication.Status.ELIGIBLE
                && application.getStatus() != AccommodationApplication.Status.WAITLISTED) {
            throw new IllegalStateException("Only eligible or waitlisted applications can be proposed for allocation.");
        }
        if (accommodationRate.getStatus() != AccommodationRate.Status.ACTIVE
                || accommodationRate.getRatingStatus() != AccommodationRate.RatingStatus.RATED) {
            throw new IllegalStateException("An active, rated accommodation rate is required.");
        }
        if (occupancyEndsOn.isBefore(occupancyStartsOn)) throw new IllegalArgumentException("Occupancy end date cannot precede its start date.");
        this.allocationNumber = AccommodationValueRules.code(allocationNumber, "Allocation number");
        this.application = application;
        this.room = room;
        this.accommodationRate = accommodationRate;
        this.occupancyStartsOn = occupancyStartsOn;
        this.occupancyEndsOn = occupancyEndsOn;
        this.status = Status.PROPOSED;
        this.allocatedByUserId = actorUserId;
        this.allocatedAt = occurredAt;
        this.billingStatus = BillingStatus.NOT_REQUESTED;
    }

    public Status approve(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.PROPOSED) throw new IllegalStateException("Only a proposed allocation can be approved.");
        if (actorUserId == null || actorUserId.equals(allocatedByUserId)) {
            throw new IllegalArgumentException("A different authorised operator must approve the room allocation.");
        }
        Status previous = status;
        approvedByUserId = actorUserId;
        approvedAt = occurredAt;
        approvalReason = AccommodationValueRules.required(reason, "Approval reason");
        status = Status.ALLOCATED;
        return previous;
    }

    public Status checkIn(UUID actorUserId, String notes, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.ALLOCATED) throw new IllegalStateException("Only an approved allocation can be checked in.");
        Status previous = status;
        checkedInByUserId = actorUserId;
        checkedInAt = occurredAt;
        checkInNotes = AccommodationValueRules.required(notes, "Check-in notes");
        status = Status.CHECKED_IN;
        return previous;
    }

    public Status checkOut(UUID actorUserId, String notes, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.CHECKED_IN) throw new IllegalStateException("Only a checked-in allocation can be checked out.");
        if (actorUserId == null || actorUserId.equals(checkedInByUserId)) {
            throw new IllegalArgumentException("A different authorised operator must check the resident out.");
        }
        Status previous = status;
        checkedOutByUserId = actorUserId;
        checkedOutAt = occurredAt;
        checkOutNotes = AccommodationValueRules.required(notes, "Check-out notes");
        status = Status.CHECKED_OUT;
        return previous;
    }

    public Status end(Status targetStatus, UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (targetStatus != Status.CANCELLED && targetStatus != Status.WITHDRAWN) {
            throw new IllegalArgumentException("Allocation ending outcome must be cancelled or withdrawn.");
        }
        if (targetStatus == Status.CANCELLED && status != Status.PROPOSED) {
            throw new IllegalStateException("Only a proposed allocation can be cancelled.");
        }
        if (targetStatus == Status.WITHDRAWN && status != Status.ALLOCATED && status != Status.CHECKED_IN) {
            throw new IllegalStateException("Only an approved or occupied allocation can be withdrawn.");
        }
        Status previous = status;
        endedByUserId = actorUserId;
        endedAt = occurredAt;
        endReason = AccommodationValueRules.required(reason, "Ending reason");
        status = targetStatus;
        return previous;
    }

    private void requireVersion(long expectedVersion) {
        AccommodationValueRules.requireVersion(getVersion(), expectedVersion, "Room allocation");
    }

    public String getAllocationNumber() { return allocationNumber; }
    public AccommodationApplication getApplication() { return application; }
    public AccommodationRoom getRoom() { return room; }
    public AccommodationRate getAccommodationRate() { return accommodationRate; }
    public LocalDate getOccupancyStartsOn() { return occupancyStartsOn; }
    public LocalDate getOccupancyEndsOn() { return occupancyEndsOn; }
    public Status getStatus() { return status; }
    public UUID getAllocatedByUserId() { return allocatedByUserId; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReason() { return approvalReason; }
    public UUID getCheckedInByUserId() { return checkedInByUserId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getCheckInNotes() { return checkInNotes; }
    public UUID getCheckedOutByUserId() { return checkedOutByUserId; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public String getCheckOutNotes() { return checkOutNotes; }
    public UUID getEndedByUserId() { return endedByUserId; }
    public Instant getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }
    public UUID getBillingEventId() { return billingEventId; }
    public BillingStatus getBillingStatus() { return billingStatus; }
}
