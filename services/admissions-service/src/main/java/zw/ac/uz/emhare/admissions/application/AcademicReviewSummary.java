package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AcademicReviewSummary(
        UUID id, UUID selectionRoundId, UUID applicationId, String applicationNumber,
        String applicantNumber, String applicantName,
        UUID programmeChoiceId, String programmeCode, String programmeName, int choiceRank,
        UUID owningAcademicUnitId, String owningAcademicUnitCode, String owningAcademicUnitName,
        UUID recommendationAcademicUnitId, String recommendationAcademicUnitCode,
        String recommendationAcademicUnitName, String hierarchyPathJson,
        String status, int releaseAttempt, UUID releasedByUserId, Instant releasedAt,
        Instant dueAt, UUID claimedByUserId, Instant claimedAt, long version,
        AcademicUnitRecommendationSummary latestRecommendation) {
    static AcademicReviewSummary from(AcademicReviewAssignment value, AcademicUnitRecommendation recommendation) {
        return new AcademicReviewSummary(value.getId(), value.getSelectionRound().getId(),
                value.getApplication().getId(), value.getApplication().getApplicationNumber(),
                value.getApplication().getApplicant().getApplicantNumber(),
                value.getApplication().getApplicant().getDisplayName(),
                value.getProgrammeChoice().getId(), value.getProgrammeChoice().getProgrammeCode(),
                value.getProgrammeChoice().getProgrammeName(), value.getChoiceRank(),
                value.getOwningAcademicUnitId(), value.getOwningAcademicUnitCode(), value.getOwningAcademicUnitName(),
                value.getRecommendationAcademicUnitId(), value.getRecommendationAcademicUnitCode(),
                value.getRecommendationAcademicUnitName(), value.getHierarchyPathJson(), value.getStatus().name(),
                value.getReleaseAttempt(), value.getReleasedByUserId(), value.getReleasedAt(), value.getDueAt(),
                value.getClaimedByUserId(), value.getClaimedAt(), value.getVersion(),
                recommendation == null ? null : AcademicUnitRecommendationSummary.from(recommendation));
    }
}
