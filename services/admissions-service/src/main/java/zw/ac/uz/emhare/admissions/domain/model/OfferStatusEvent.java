package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

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

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_status_events")
public class OfferStatusEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private AdmissionOffer offer;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "changed_by_user_id", nullable = false)
    private UUID changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected OfferStatusEvent() {
    }

    public OfferStatusEvent(AdmissionOffer offer, OfferStatus fromStatus, OfferStatus toStatus, String reason, UUID actorUserId, Instant changedAt) {
        this.offer = offer;
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus.name();
        this.reason = reason;
        this.changedByUserId = actorUserId;
        this.changedAt = changedAt;
    }
}
