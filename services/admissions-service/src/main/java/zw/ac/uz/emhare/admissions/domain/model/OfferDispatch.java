package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_dispatches")
public class OfferDispatch extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private AdmissionOffer offer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_publication_id")
    private OfferPublication offerPublication;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "notification_event_id")
    private java.util.UUID notificationEventId;

    @Column(name = "delivery_method_code", nullable = false, length = 40)
    private String deliveryMethodCode;

    @Column(name = "sent_to", nullable = false, length = 250)
    private String sentTo;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferDispatchStatus status;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    protected OfferDispatch() {
    }

    public OfferDispatch(
            AdmissionOffer offer,
            String deliveryMethodCode,
            String sentTo,
            String providerMessageId,
            Instant sentAt) {
        if (deliveryMethodCode == null || deliveryMethodCode.isBlank() || sentTo == null || sentTo.isBlank()) {
            throw new IllegalArgumentException("Offer delivery method and recipient are required.");
        }
        this.offer = offer;
        this.deliveryMethodCode = deliveryMethodCode.trim().toUpperCase();
        this.sentTo = sentTo.trim();
        this.sentAt = sentAt;
        this.status = OfferDispatchStatus.SENT;
        this.providerMessageId = providerMessageId == null || providerMessageId.isBlank() ? null : providerMessageId.trim();
        this.attemptNumber = 1;
    }

    public OfferDispatch(AdmissionOffer offer, OfferPublication publication, int attemptNumber,
            java.util.UUID notificationEventId, String sentTo, Instant queuedAt) {
        if (offer == null || publication == null || attemptNumber < 1 || notificationEventId == null
                || sentTo == null || sentTo.isBlank()) {
            throw new IllegalArgumentException("Offer publication, delivery attempt, event and recipient are required.");
        }
        this.offer = offer;
        this.offerPublication = publication;
        this.attemptNumber = attemptNumber;
        this.notificationEventId = notificationEventId;
        this.deliveryMethodCode = "EMAIL";
        this.sentTo = sentTo.trim();
        this.status = OfferDispatchStatus.QUEUED;
        this.sentAt = queuedAt;
    }

    public void recordStatus(OfferDispatchStatus status, String providerMessageId,
            String failureReason, Instant occurredAt) {
        if (occurredAt == null) throw new IllegalArgumentException("Delivery evidence time is required.");
        if (sentAt != null && occurredAt.isBefore(sentAt)) return;
        if ((status == OfferDispatchStatus.FAILED || status == OfferDispatchStatus.BOUNCED)
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("Failed or bounced delivery requires a reason.");
        }
        this.status = status;
        this.providerMessageId = providerMessageId == null || providerMessageId.isBlank() ? null : providerMessageId.trim();
        this.failureReason = failureReason == null || failureReason.isBlank() ? null : failureReason.trim();
        this.sentAt = occurredAt;
    }

    public String getDeliveryMethodCode() { return deliveryMethodCode; }
    public OfferPublication getOfferPublication() { return offerPublication; }
    public int getAttemptNumber() { return attemptNumber; }
    public java.util.UUID getNotificationEventId() { return notificationEventId; }
    public String getSentTo() { return sentTo; }
    public Instant getSentAt() { return sentAt; }
    public OfferDispatchStatus getStatus() { return status; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getFailureReason() { return failureReason; }
}
