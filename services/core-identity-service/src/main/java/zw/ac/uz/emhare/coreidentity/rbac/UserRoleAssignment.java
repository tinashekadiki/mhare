package zw.ac.uz.emhare.coreidentity.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "user_role_assignments")
public class UserRoleAssignment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private PlatformUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "academic_unit_id")
    private UUID academicUnitId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(PlatformUser user, Role role, UUID academicUnitId, Instant startsAt) {
        this.user = user;
        this.role = role;
        this.academicUnitId = academicUnitId;
        this.startsAt = startsAt;
    }

    public boolean isActiveAt(Instant instant) {
        return !startsAt.isAfter(instant) && (endsAt == null || endsAt.isAfter(instant));
    }

    public PlatformUser getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public UUID getAcademicUnitId() {
        return academicUnitId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void end(Instant endedAt) {
        endsAt = endedAt;
    }
}
