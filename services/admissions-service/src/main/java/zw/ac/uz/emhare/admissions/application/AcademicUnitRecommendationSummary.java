package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AcademicUnitRecommendation;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AcademicUnitRecommendationSummary(
        UUID id, UUID assignmentId, int sequence, String recommendation, Integer rankPosition,
        String quotaTypeCode, String reason, UUID recommendedByUserId, Instant recommendedAt,
        String reviewStatus, UUID reviewedByUserId, Instant reviewedAt, String reviewReason,
        String finalDecision, long version) {
    static AcademicUnitRecommendationSummary from(AcademicUnitRecommendation value) {
        return new AcademicUnitRecommendationSummary(value.getId(), value.getAssignment().getId(),
                value.getRecommendationSequence(), value.getRecommendation().name(), value.getRankPosition(),
                value.getQuotaTypeCode(), value.getReason(), value.getRecommendedByUserId(), value.getRecommendedAt(),
                value.getReviewStatus().name(), value.getReviewedByUserId(), value.getReviewedAt(), value.getReviewReason(),
                value.getFinalDecision() == null ? null : value.getFinalDecision().name(), value.getVersion());
    }
}
