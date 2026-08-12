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
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_batches")
public class OfferBatch extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_cycle_id", nullable = false)
    private AdmissionCycle admissionCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selection_round_id", nullable = false)
    private SelectionRound selectionRound;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private OfferBatchScopeType scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferBatchStatus status;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected OfferBatch() {
    }

    public OfferBatch(
            AdmissionCycle admissionCycle,
            SelectionRound selectionRound,
            String code,
            String name,
            OfferBatchScopeType scopeType,
            UUID scopeId) {
        if (!admissionCycle.getId().equals(selectionRound.getAdmissionCycle().getId())) {
            throw new IllegalArgumentException("Offer batch and selection round must belong to the same intake.");
        }
        if ((scopeType == OfferBatchScopeType.INSTITUTION && scopeId != null)
                || (scopeType != OfferBatchScopeType.INSTITUTION && scopeId == null)) {
            throw new IllegalArgumentException("Offer batch scope ID does not match its scope type.");
        }
        this.admissionCycle = admissionCycle;
        this.selectionRound = selectionRound;
        this.code = requireText(code, "Offer batch code");
        this.name = requireText(name, "Offer batch name");
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.status = OfferBatchStatus.DRAFT;
    }

    public void approve(UUID actorUserId, Instant now) {
        if (status != OfferBatchStatus.DRAFT) {
            throw new IllegalStateException("Only a draft offer batch can be approved.");
        }
        if (selectionRound.getStatus() != SelectionRoundStatus.APPROVED) {
            throw new IllegalStateException("The selection round must be approved before approving its offer batch.");
        }
        status = OfferBatchStatus.APPROVED;
        approvedByUserId = actorUserId;
        approvedAt = now;
    }

    public void markDispatched(Instant now) {
        if (status != OfferBatchStatus.APPROVED) {
            throw new IllegalStateException("Only an approved offer batch can be marked dispatched.");
        }
        status = OfferBatchStatus.DISPATCHED;
        dispatchedAt = now;
    }

    public void close(Instant now) {
        if (status != OfferBatchStatus.DISPATCHED) {
            throw new IllegalStateException("Only a dispatched offer batch can be closed.");
        }
        status = OfferBatchStatus.CLOSED;
        closedAt = now;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    public AdmissionCycle getAdmissionCycle() { return admissionCycle; }
    public SelectionRound getSelectionRound() { return selectionRound; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public OfferBatchScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public OfferBatchStatus getStatus() { return status; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getClosedAt() { return closedAt; }
}
