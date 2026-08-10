package zw.ac.uz.emhare.dining.setup;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "dining_halls") @SQLRestriction("deleted_at IS NULL")
public class DiningHall extends AuditableEntity {
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "location_description", nullable = false, length = 300) private String locationDescription;
    @Column(name = "service_capacity", nullable = false) private int serviceCapacity;
    @Column(nullable = false) private boolean active;
    protected DiningHall() {}
    public DiningHall(String code, String name, String locationDescription, int serviceCapacity) {
        updateValues(code, name, locationDescription, serviceCapacity, true);
    }
    public void update(String code, String name, String locationDescription, int serviceCapacity, boolean active, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Dining hall");
        updateValues(code, name, locationDescription, serviceCapacity, active);
    }
    private void updateValues(String code, String name, String locationDescription, int serviceCapacity, boolean active) {
        if (serviceCapacity < 1) throw new IllegalArgumentException("Dining hall service capacity must be positive.");
        this.code = DiningValues.code(code, "Dining hall code"); this.name = DiningValues.required(name, "Dining hall name");
        this.locationDescription = DiningValues.required(locationDescription, "Dining hall location");
        this.serviceCapacity = serviceCapacity; this.active = active;
    }
    public String getCode() { return code; } public String getName() { return name; }
    public String getLocationDescription() { return locationDescription; } public int getServiceCapacity() { return serviceCapacity; }
    public boolean isActive() { return active; }
}
