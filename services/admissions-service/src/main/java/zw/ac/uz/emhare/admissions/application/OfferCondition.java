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
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_conditions")
public class OfferCondition extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private AdmissionOffer offer;

    @Column(name = "condition_code", nullable = false, length = 60)
    private String conditionCode;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferConditionStatus status;

    @Column(name = "satisfied_by_user_id")
    private UUID satisfiedByUserId;

    @Column(name = "satisfied_at")
    private Instant satisfiedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    protected OfferCondition() {
    }

    public OfferCondition(AdmissionOffer offer, String conditionCode, String description, boolean required) {
        if (conditionCode == null || conditionCode.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Offer condition code and description are required.");
        }
        this.offer = offer;
        this.conditionCode = conditionCode.trim().toUpperCase();
        this.description = description.trim();
        this.required = required;
        this.status = OfferConditionStatus.PENDING;
    }

    public void satisfy(UUID actorUserId, String notes, Instant now) {
        resolve(OfferConditionStatus.SATISFIED, actorUserId, notes, now);
    }

    public void waive(UUID actorUserId, String notes, Instant now) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("A condition waiver reason is required.");
        }
        resolve(OfferConditionStatus.WAIVED, actorUserId, notes, now);
    }

    private void resolve(OfferConditionStatus resolution, UUID actorUserId, String notes, Instant now) {
        if (status != OfferConditionStatus.PENDING) {
            throw new IllegalStateException("Offer condition has already been resolved.");
        }
        status = resolution;
        satisfiedByUserId = actorUserId;
        satisfiedAt = now;
        resolutionNotes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public String getConditionCode() { return conditionCode; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public OfferConditionStatus getStatus() { return status; }
    public UUID getSatisfiedByUserId() { return satisfiedByUserId; }
    public Instant getSatisfiedAt() { return satisfiedAt; }
    public String getResolutionNotes() { return resolutionNotes; }
}
