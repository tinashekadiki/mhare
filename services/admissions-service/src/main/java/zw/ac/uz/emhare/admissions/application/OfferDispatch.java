package zw.ac.uz.emhare.admissions.application;

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

    @Column(name = "delivery_method_code", nullable = false, length = 40)
    private String deliveryMethodCode;

    @Column(name = "sent_to", nullable = false, length = 250)
    private String sentTo;

    @Column(name = "sent_at", nullable = false)
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
    }

    public String getDeliveryMethodCode() { return deliveryMethodCode; }
    public String getSentTo() { return sentTo; }
    public Instant getSentAt() { return sentAt; }
    public OfferDispatchStatus getStatus() { return status; }
    public String getProviderMessageId() { return providerMessageId; }
}
