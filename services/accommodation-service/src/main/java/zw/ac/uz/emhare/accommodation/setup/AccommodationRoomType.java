package zw.ac.uz.emhare.accommodation.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_room_types")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationRoomType extends AuditableEntity {
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 500) private String description;
    @Column(name = "default_capacity", nullable = false) private int defaultCapacity;
    @Column(nullable = false) private boolean active;

    protected AccommodationRoomType() {}

    public AccommodationRoomType(String code, String name, String description, int defaultCapacity) {
        updateValues(code, name, description, defaultCapacity, true);
    }

    public void update(String code, String name, String description, int defaultCapacity, boolean active,
            long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("The record was changed by another operator. Refresh and try again.");
        updateValues(code, name, description, defaultCapacity, active);
    }

    private void updateValues(String code, String name, String description, int defaultCapacity, boolean active) {
        if (defaultCapacity < 1) throw new IllegalArgumentException("Default room capacity must be positive.");
        this.code = AccommodationPremise.required(code, "Room type code").toUpperCase();
        this.name = AccommodationPremise.required(name, "Room type name");
        this.description = AccommodationPremise.optional(description);
        this.defaultCapacity = defaultCapacity;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDefaultCapacity() { return defaultCapacity; }
    public boolean isActive() { return active; }
}
