package zw.ac.uz.emhare.admissions.domain.model;

import zw.ac.uz.emhare.admissions.application.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Advisory recommendation recorded by authorised highest-unit staff. @author Tinashe K */
@Audited
@Entity
@Table(name = "academic_unit_recommendations")
@SQLRestriction("deleted_at IS NULL")
public class AcademicUnitRecommendation extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_review_assignment_id", nullable = false)
    private AcademicReviewAssignment assignment;
    @Column(name = "recommendation_sequence", nullable = false)
    private int recommendationSequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SelectionDecisionType recommendation;
    @Column(name = "rank_position") private Integer rankPosition;
    @Column(name = "quota_type_code", length = 50) private String quotaTypeCode;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "recommended_by_user_id", nullable = false) private UUID recommendedByUserId;
    @Column(name = "recommended_at", nullable = false) private Instant recommendedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private AcademicRecommendationReviewStatus reviewStatus;
    @Column(name = "reviewed_by_user_id") private UUID reviewedByUserId;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason", length = 1000) private String reviewReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", length = 30) private SelectionDecisionType finalDecision;

    protected AcademicUnitRecommendation() { }

    public AcademicUnitRecommendation(AcademicReviewAssignment assignment, int sequence,
            SelectionDecisionType recommendation, Integer rankPosition, String quotaTypeCode,
            String reason, UUID actorUserId, Instant now) {
        this.assignment = assignment;
        this.recommendationSequence = sequence;
        this.recommendation = recommendation;
        this.rankPosition = rankPosition;
        this.quotaTypeCode = optional(quotaTypeCode);
        this.reason = required(reason, "Recommendation reason");
        this.recommendedByUserId = actorUserId;
        this.recommendedAt = now;
        this.reviewStatus = AcademicRecommendationReviewStatus.PENDING;
    }

    public void approve(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.APPROVED, recommendation, actorUserId, reason, now);
    }
    public void override(SelectionDecisionType decision, UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.OVERRIDDEN, decision, actorUserId, reason, now);
    }
    public void returnForReconsideration(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.RETURNED, null, actorUserId, reason, now);
    }
    private void review(AcademicRecommendationReviewStatus status, SelectionDecisionType decision,
            UUID actorUserId, String reason, Instant now) {
        if (reviewStatus != AcademicRecommendationReviewStatus.PENDING) {
            throw new IllegalStateException("Recommendation has already been reviewed.");
        }
        reviewStatus = status;
        finalDecision = decision;
        reviewedByUserId = actorUserId;
        reviewedAt = now;
        reviewReason = required(reason, "Admissions review reason");
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public AcademicReviewAssignment getAssignment() { return assignment; }
    public int getRecommendationSequence() { return recommendationSequence; }
    public SelectionDecisionType getRecommendation() { return recommendation; }
    public String getRecommendationCode() { return recommendation.name(); }
    public Integer getRankPosition() { return rankPosition; }
    public String getQuotaTypeCode() { return quotaTypeCode; }
    public String getReason() { return reason; }
    public UUID getRecommendedByUserId() { return recommendedByUserId; }
    public Instant getRecommendedAt() { return recommendedAt; }
    public AcademicRecommendationReviewStatus getReviewStatus() { return reviewStatus; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
    public SelectionDecisionType getFinalDecision() { return finalDecision; }
}
