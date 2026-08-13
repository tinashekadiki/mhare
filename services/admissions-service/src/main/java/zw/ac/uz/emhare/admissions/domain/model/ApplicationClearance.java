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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Audited Admissions confirmation evidence. @author Tinashe K */
@Audited
@Entity
@Table(name = "application_clearances")
@SQLRestriction("deleted_at IS NULL")
public class ApplicationClearance extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationClearanceOutcome outcome;

    @Column(name = "payment_cleared", nullable = false)
    private boolean paymentCleared;
    @Column(name = "sections_complete", nullable = false)
    private boolean sectionsComplete;
    @Column(name = "required_documents_verified", nullable = false)
    private boolean requiredDocumentsVerified;
    @Column(name = "qualifications_verified", nullable = false)
    private boolean qualificationsVerified;
    @Column(name = "duplicate_checks_passed", nullable = false)
    private boolean duplicateChecksPassed;
    @Column(name = "duplicate_check_summary", nullable = false, length = 1000)
    private String duplicateCheckSummary;
    @Column(name = "confirmed_by_user_id", nullable = false)
    private UUID confirmedByUserId;
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "invalidated_by_user_id")
    private UUID invalidatedByUserId;
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;
    @Column(name = "invalidation_reason", length = 1000)
    private String invalidationReason;

    protected ApplicationClearance() {
    }

    public ApplicationClearance(
            Application application,
            UUID actorUserId,
            String reason,
            String duplicateCheckSummary,
            Instant now) {
        this.application = application;
        this.outcome = ApplicationClearanceOutcome.CONFIRMED;
        this.paymentCleared = true;
        this.sectionsComplete = true;
        this.requiredDocumentsVerified = true;
        this.qualificationsVerified = true;
        this.duplicateChecksPassed = true;
        this.duplicateCheckSummary = requireDuplicateCheckSummary(duplicateCheckSummary);
        this.confirmedByUserId = actorUserId;
        this.confirmedAt = now;
        this.reason = requireReason(reason);
    }

    public void invalidate(UUID actorUserId, String reason, Instant now) {
        if (outcome != ApplicationClearanceOutcome.CONFIRMED) {
            throw new IllegalStateException("Application clearance is already invalidated.");
        }
        outcome = ApplicationClearanceOutcome.INVALIDATED;
        invalidatedByUserId = actorUserId;
        invalidatedAt = now;
        invalidationReason = requireReason(reason);
    }

    public ApplicationClearanceOutcome getOutcome() { return outcome; }
    public UUID getConfirmedByUserId() { return confirmedByUserId; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getReason() { return reason; }
    public boolean isDuplicateChecksPassed() { return duplicateChecksPassed; }
    public String getDuplicateCheckSummary() { return duplicateCheckSummary; }

    private static String requireReason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A clearance reason is required.");
        return value.trim();
    }

    private static String requireDuplicateCheckSummary(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Duplicate-check evidence is required.");
        }
        return value.trim();
    }
}
