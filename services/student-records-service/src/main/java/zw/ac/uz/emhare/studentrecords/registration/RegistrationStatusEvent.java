package zw.ac.uz.emhare.studentrecords.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "registration_status_events")
@SQLRestriction("deleted_at IS NULL")
public class RegistrationStatusEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_session_id", nullable = false)
    private RegistrationSession registrationSession;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private RegistrationStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private RegistrationStatus toStatus;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "changed_by_user_id", nullable = false)
    private UUID changedByUserId;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected RegistrationStatusEvent() {
    }

    public RegistrationStatusEvent(
            RegistrationSession registrationSession,
            RegistrationStatus fromStatus,
            RegistrationStatus toStatus,
            String reason,
            UUID changedByUserId,
            Instant changedAt) {
        this.registrationSession = registrationSession;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason.trim();
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
    }
}
