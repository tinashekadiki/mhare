package zw.ac.uz.emhare.admissions.domain.model;

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

/** Portal publication and independent email-delivery evidence for an offer document. @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_publications")
@SQLRestriction("deleted_at IS NULL")
public class OfferPublication extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private AdmissionOffer offer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_document_version_id", nullable = false)
    private OfferDocumentVersion documentVersion;
    @Column(name = "publication_sequence", nullable = false) private int publicationSequence;
    @Column(name = "portal_published_at", nullable = false) private Instant portalPublishedAt;
    @Column(name = "published_by_user_id", nullable = false) private UUID publishedByUserId;
    @Column(name = "notification_event_id", nullable = false) private UUID notificationEventId;
    @Enumerated(EnumType.STRING)
    @Column(name = "email_delivery_status", nullable = false, length = 30)
    private OfferEmailDeliveryStatus emailDeliveryStatus;
    @Column(name = "provider_message_id", length = 240) private String providerMessageId;
    @Column(name = "email_status_at", nullable = false) private Instant emailStatusAt;
    @Column(name = "email_failure_reason", length = 1000) private String emailFailureReason;
    @Column(name = "current_publication", nullable = false) private boolean currentPublication;
    @Column(name = "superseded_at") private Instant supersededAt;

    protected OfferPublication() { }

    public OfferPublication(AdmissionOffer offer, OfferDocumentVersion documentVersion,
            int publicationSequence, UUID publishedByUserId, UUID notificationEventId, Instant now) {
        if (documentVersion.getStatus() != OfferDocumentVersionStatus.STORED) {
            throw new IllegalStateException("Only a stored offer document can be published.");
        }
        this.offer = offer;
        this.documentVersion = documentVersion;
        this.publicationSequence = publicationSequence;
        this.publishedByUserId = publishedByUserId;
        this.notificationEventId = notificationEventId;
        this.portalPublishedAt = now;
        this.emailDeliveryStatus = OfferEmailDeliveryStatus.QUEUED;
        this.emailStatusAt = now;
        this.currentPublication = true;
    }

    public void supersede(Instant now) {
        if (!currentPublication) return;
        currentPublication = false;
        supersededAt = now;
    }

    public void recordEmailStatus(OfferEmailDeliveryStatus status, String providerMessageId,
            String failureReason, Instant occurredAt) {
        if (occurredAt.isBefore(emailStatusAt)) return;
        if ((status == OfferEmailDeliveryStatus.FAILED || status == OfferEmailDeliveryStatus.BOUNCED)
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("Failed or bounced email evidence requires a reason.");
        }
        emailDeliveryStatus = status;
        this.providerMessageId = providerMessageId == null || providerMessageId.isBlank() ? null : providerMessageId.trim();
        emailFailureReason = failureReason == null || failureReason.isBlank() ? null : failureReason.trim();
        emailStatusAt = occurredAt;
    }

    public AdmissionOffer getOffer() { return offer; }
    public OfferDocumentVersion getDocumentVersion() { return documentVersion; }
    public int getPublicationSequence() { return publicationSequence; }
    public Instant getPortalPublishedAt() { return portalPublishedAt; }
    public UUID getPublishedByUserId() { return publishedByUserId; }
    public UUID getNotificationEventId() { return notificationEventId; }
    public OfferEmailDeliveryStatus getEmailDeliveryStatus() { return emailDeliveryStatus; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getEmailStatusAt() { return emailStatusAt; }
    public String getEmailFailureReason() { return emailFailureReason; }
    public boolean isCurrentPublication() { return currentPublication; }
    public Instant getSupersededAt() { return supersededAt; }
}
