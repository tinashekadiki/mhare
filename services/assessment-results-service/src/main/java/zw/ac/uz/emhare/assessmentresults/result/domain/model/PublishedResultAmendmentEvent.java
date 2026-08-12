package zw.ac.uz.emhare.assessmentresults.result.domain.model;

import zw.ac.uz.emhare.assessmentresults.result.*;

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
@Table(name = "published_result_amendment_events")
@SQLRestriction("deleted_at IS NULL")
public class PublishedResultAmendmentEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_result_amendment_id", nullable = false)
    private PublishedResultAmendment amendment;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private PublishedResultAmendment.Status fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private PublishedResultAmendment.Status toStatus;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PublishedResultAmendmentEvent() {
    }

    public PublishedResultAmendmentEvent(
            PublishedResultAmendment amendment,
            PublishedResultAmendment.Status fromStatus,
            String reason,
            UUID actorUserId,
            Instant occurredAt) {
        this.amendment = amendment;
        this.fromStatus = fromStatus;
        this.toStatus = amendment.getStatus();
        this.reason = GradingScheme.text(reason);
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }
}
