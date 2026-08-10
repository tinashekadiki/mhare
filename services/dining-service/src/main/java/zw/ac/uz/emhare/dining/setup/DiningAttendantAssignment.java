package zw.ac.uz.emhare.dining.setup;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "dining_attendant_assignments")
@SQLRestriction("deleted_at IS NULL")
public class DiningAttendantAssignment extends AuditableEntity {
    public enum Role { ATTENDANT, SUPERVISOR, MANAGER }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dining_hall_id")
    private DiningHall diningHall;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "staff_number", nullable = false, length = 40)
    private String staffNumber;

    @Column(name = "staff_name", nullable = false, length = 200)
    private String staffName;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 30)
    private Role roleCode;

    @Column(nullable = false)
    private boolean active;

    protected DiningAttendantAssignment() {}

    public DiningAttendantAssignment(DiningHall diningHall, UUID staffId, String staffNumber, String staffName,
            LocalDate effectiveFrom, LocalDate effectiveUntil, Role roleCode) {
        updateValues(diningHall, staffId, staffNumber, staffName, effectiveFrom, effectiveUntil, roleCode, true);
    }

    public void update(DiningHall diningHall, UUID staffId, String staffNumber, String staffName,
            LocalDate effectiveFrom, LocalDate effectiveUntil, Role roleCode, boolean active, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Dining attendant assignment");
        updateValues(diningHall, staffId, staffNumber, staffName, effectiveFrom, effectiveUntil, roleCode, active);
    }

    private void updateValues(DiningHall diningHall, UUID staffId, String staffNumber, String staffName,
            LocalDate effectiveFrom, LocalDate effectiveUntil, Role roleCode, boolean active) {
        if (diningHall == null || !diningHall.isActive()) {
            throw new IllegalArgumentException("An active dining hall is required.");
        }
        if (staffId == null || effectiveFrom == null || roleCode == null) {
            throw new IllegalArgumentException("Staff, effective date, and role are required.");
        }
        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Attendant effective end date cannot precede the start date.");
        }
        this.diningHall = diningHall;
        this.staffId = staffId;
        this.staffNumber = DiningValues.code(staffNumber, "Staff number");
        this.staffName = DiningValues.required(staffName, "Staff name");
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.roleCode = roleCode;
        this.active = active;
    }

    public DiningHall getDiningHall() { return diningHall; }
    public UUID getStaffId() { return staffId; }
    public String getStaffNumber() { return staffNumber; }
    public String getStaffName() { return staffName; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveUntil() { return effectiveUntil; }
    public Role getRoleCode() { return roleCode; }
    public boolean isActive() { return active; }
}
