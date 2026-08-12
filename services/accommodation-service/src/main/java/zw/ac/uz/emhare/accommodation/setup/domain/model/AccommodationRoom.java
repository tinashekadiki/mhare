package zw.ac.uz.emhare.accommodation.setup.domain.model;

import zw.ac.uz.emhare.accommodation.setup.*;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_rooms")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationRoom extends AuditableEntity {
    public enum ConditionStatus { AVAILABLE, MAINTENANCE, OUT_OF_SERVICE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residence_hall_id")
    private ResidenceHall residenceHall;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id")
    private AccommodationRoomType roomType;
    @Column(nullable = false, length = 40) private String code;
    @Column(name = "floor_label", length = 40) private String floorLabel;
    @Column(nullable = false) private int capacity;
    @Column(name = "accessibility_ready", nullable = false) private boolean accessibilityReady;
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", nullable = false, length = 20)
    private ConditionStatus conditionStatus;
    @Column(name = "condition_notes", length = 500) private String conditionNotes;
    @Column(name = "reserved_for_group_id") private UUID reservedForGroupId;
    @Column(nullable = false) private boolean active;

    protected AccommodationRoom() {}

    public AccommodationRoom(ResidenceHall residenceHall, AccommodationRoomType roomType, String code,
            String floorLabel, int capacity, boolean accessibilityReady, ConditionStatus conditionStatus,
            String conditionNotes, UUID reservedForGroupId) {
        updateValues(residenceHall, roomType, code, floorLabel, capacity, accessibilityReady,
                conditionStatus, conditionNotes, reservedForGroupId, true);
    }

    public void update(ResidenceHall residenceHall, AccommodationRoomType roomType, String code,
            String floorLabel, int capacity, boolean accessibilityReady, ConditionStatus conditionStatus,
            String conditionNotes, UUID reservedForGroupId, boolean active, long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("The record was changed by another operator. Refresh and try again.");
        updateValues(residenceHall, roomType, code, floorLabel, capacity, accessibilityReady,
                conditionStatus, conditionNotes, reservedForGroupId, active);
    }

    private void updateValues(ResidenceHall residenceHall, AccommodationRoomType roomType, String code,
            String floorLabel, int capacity, boolean accessibilityReady, ConditionStatus conditionStatus,
            String conditionNotes, UUID reservedForGroupId, boolean active) {
        if (residenceHall == null || !residenceHall.isActive()) throw new IllegalArgumentException("An active residence hall is required.");
        if (roomType == null || !roomType.isActive()) throw new IllegalArgumentException("An active room type is required.");
        if (capacity < 1) throw new IllegalArgumentException("Room capacity must be positive.");
        this.residenceHall = residenceHall;
        this.roomType = roomType;
        this.code = AccommodationPremise.required(code, "Room code").toUpperCase();
        this.floorLabel = AccommodationPremise.optional(floorLabel);
        this.capacity = capacity;
        this.accessibilityReady = accessibilityReady;
        this.conditionStatus = conditionStatus == null ? ConditionStatus.AVAILABLE : conditionStatus;
        this.conditionNotes = AccommodationPremise.optional(conditionNotes);
        this.reservedForGroupId = reservedForGroupId;
        this.active = active;
    }

    public ResidenceHall getResidenceHall() { return residenceHall; }
    public AccommodationRoomType getRoomType() { return roomType; }
    public String getCode() { return code; }
    public String getFloorLabel() { return floorLabel; }
    public int getCapacity() { return capacity; }
    public boolean isAccessibilityReady() { return accessibilityReady; }
    public ConditionStatus getConditionStatus() { return conditionStatus; }
    public String getConditionNotes() { return conditionNotes; }
    public UUID getReservedForGroupId() { return reservedForGroupId; }
    public boolean isActive() { return active; }
}
