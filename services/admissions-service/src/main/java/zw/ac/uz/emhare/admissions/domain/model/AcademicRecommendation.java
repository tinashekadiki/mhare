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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * Successor to {@link AcademicUnitRecommendation} per ADR-0014: references {@link AcademicReview}
 * instead of {@link AcademicReviewAssignment}; ranking and quota fields are dropped because
 * ranking and quota category no longer factor into a recommendation. @author Tinashe K
 */
@Audited
@Entity
@Table(name = "academic_recommendations",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_recommendation_sequence",
                columnNames = {"academic_review_id", "recommendation_sequence"}))
@SQLRestriction("deleted_at IS NULL")
public class AcademicRecommendation extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_review_id", nullable = false)
    private AcademicReview academicReview;
    @Column(name = "recommendation_sequence", nullable = false)
    private int recommendationSequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationOutcome recommendation;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "recommended_by_user_id", nullable = false)
    private UUID recommendedByUserId;
    @Column(name = "recommended_at", nullable = false)
    private Instant recommendedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private AcademicRecommendationReviewStatus reviewStatus;
    @Column(name = "reviewed_by_user_id") private UUID reviewedByUserId;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason", length = 1000) private String reviewReason;

    protected AcademicRecommendation() { }

    public AcademicRecommendation(AcademicReview academicReview, int sequence,
            RecommendationOutcome recommendation, String reason, UUID actorUserId, Instant now) {
        this.academicReview = academicReview;
        this.recommendationSequence = sequence;
        this.recommendation = recommendation;
        this.reason = required(reason, "Recommendation reason");
        this.recommendedByUserId = actorUserId;
        this.recommendedAt = now;
        this.reviewStatus = AcademicRecommendationReviewStatus.PENDING;
    }

    public void approve(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.APPROVED, actorUserId, reason, now);
    }
    public void override(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.OVERRIDDEN, actorUserId, reason, now);
    }
    public void returnForReconsideration(UUID actorUserId, String reason, Instant now) {
        review(AcademicRecommendationReviewStatus.RETURNED, actorUserId, reason, now);
    }
    private void review(AcademicRecommendationReviewStatus status, UUID actorUserId, String reason, Instant now) {
        if (reviewStatus != AcademicRecommendationReviewStatus.PENDING) {
            throw new IllegalStateException("Recommendation has already been reviewed.");
        }
        reviewStatus = status;
        reviewedByUserId = actorUserId;
        reviewedAt = now;
        reviewReason = required(reason, "Admissions review reason");
    }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }
    public AcademicReview getAcademicReview() { return academicReview; }
    public int getRecommendationSequence() { return recommendationSequence; }
    public RecommendationOutcome getRecommendation() { return recommendation; }
    public String getRecommendationCode() { return recommendation.name(); }
    public String getReason() { return reason; }
    public UUID getRecommendedByUserId() { return recommendedByUserId; }
    public Instant getRecommendedAt() { return recommendedAt; }
    public AcademicRecommendationReviewStatus getReviewStatus() { return reviewStatus; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewReason() { return reviewReason; }
}
