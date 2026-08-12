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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "published_result_amendments")
@SQLRestriction("deleted_at IS NULL")
public class PublishedResultAmendment extends AuditableEntity {

    public enum Status { REQUESTED, REVIEWED, APPROVED, APPLIED, REJECTED }

    @Column(name = "amendment_number", nullable = false, length = 60)
    private String amendmentNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_published_result_id", nullable = false)
    private PublishedResult originalPublishedResult;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "replacement_result_batch_id", nullable = false)
    private ResultBatch replacementResultBatch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "replacement_module_result_id", nullable = false)
    private ModuleResult replacementModuleResult;
    @Column(name = "proposed_final_mark", nullable = false, precision = 6, scale = 2)
    private BigDecimal proposedFinalMark;
    @Column(name = "proposed_grade", nullable = false, length = 10)
    private String proposedGrade;
    @Column(name = "proposed_remark", nullable = false, length = 100)
    private String proposedRemark;
    @Column(name = "request_reason", nullable = false, length = 1000)
    private String requestReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;
    @Column(name = "applied_by_user_id")
    private UUID appliedByUserId;
    @Column(name = "applied_at")
    private Instant appliedAt;
    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    protected PublishedResultAmendment() {
    }

    public PublishedResultAmendment(
            String amendmentNumber,
            PublishedResult originalPublishedResult,
            ModuleResult replacementModuleResult,
            String requestReason,
            UUID requestedByUserId,
            Instant requestedAt) {
        this.amendmentNumber = requiredText(amendmentNumber);
        this.originalPublishedResult = originalPublishedResult;
        this.replacementResultBatch = replacementModuleResult.getResultBatch();
        this.replacementModuleResult = replacementModuleResult;
        this.proposedFinalMark = replacementModuleResult.getFinalMark();
        this.proposedGrade = replacementModuleResult.getGrade();
        this.proposedRemark = replacementModuleResult.getRemark();
        this.requestReason = requiredText(requestReason);
        this.status = Status.REQUESTED;
        this.requestedByUserId = requestedByUserId;
        this.requestedAt = requestedAt;
    }

    public Status review(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(Status.REQUESTED);
        requireDifferentActor(actorUserId, requestedByUserId, "The requester cannot review the same amendment.");
        Status previousStatus = status;
        status = Status.REVIEWED;
        reviewedByUserId = actorUserId;
        reviewedAt = occurredAt;
        reviewReason = requiredText(reason);
        return previousStatus;
    }

    public Status approve(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(Status.REVIEWED);
        requireDifferentActor(actorUserId, requestedByUserId, "The requester cannot approve the same amendment.");
        requireDifferentActor(actorUserId, reviewedByUserId, "The reviewer cannot approve the same amendment.");
        Status previousStatus = status;
        status = Status.APPROVED;
        approvedByUserId = actorUserId;
        approvedAt = occurredAt;
        approvalReason = requiredText(reason);
        return previousStatus;
    }

    public Status apply(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        requireStatus(Status.APPROVED);
        requireDifferentActor(actorUserId, approvedByUserId, "The approver cannot apply the same amendment.");
        Status previousStatus = status;
        status = Status.APPLIED;
        appliedByUserId = actorUserId;
        appliedAt = occurredAt;
        return previousStatus;
    }

    public Status reject(UUID actorUserId, String reason, Instant occurredAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.REQUESTED && status != Status.REVIEWED) {
            throw new IllegalStateException("Only a requested or reviewed amendment can be rejected.");
        }
        requireDifferentActor(actorUserId, requestedByUserId, "The requester cannot reject the same amendment.");
        Status previousStatus = status;
        status = Status.REJECTED;
        rejectedByUserId = actorUserId;
        rejectedAt = occurredAt;
        rejectionReason = requiredText(reason);
        return previousStatus;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Published result amendment changed. Refresh before retrying.");
        }
    }

    private void requireStatus(Status requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Published result amendment is not at the required workflow stage.");
        }
    }

    private static void requireDifferentActor(UUID actorUserId, UUID priorActorUserId, String message) {
        if (actorUserId.equals(priorActorUserId)) {
            throw new IllegalStateException(message);
        }
    }

    private static String requiredText(String value) {
        return GradingScheme.text(value);
    }

    public String getAmendmentNumber() { return amendmentNumber; }
    public PublishedResult getOriginalPublishedResult() { return originalPublishedResult; }
    public ResultBatch getReplacementResultBatch() { return replacementResultBatch; }
    public ModuleResult getReplacementModuleResult() { return replacementModuleResult; }
    public BigDecimal getProposedFinalMark() { return proposedFinalMark; }
    public String getProposedGrade() { return proposedGrade; }
    public String getProposedRemark() { return proposedRemark; }
    public String getRequestReason() { return requestReason; }
    public Status getStatus() { return status; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public Instant getRequestedAt() { return requestedAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReason() { return approvalReason; }
    public UUID getAppliedByUserId() { return appliedByUserId; }
    public Instant getAppliedAt() { return appliedAt; }
    public UUID getRejectedByUserId() { return rejectedByUserId; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
