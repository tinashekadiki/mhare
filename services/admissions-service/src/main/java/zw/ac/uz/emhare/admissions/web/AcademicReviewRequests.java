package zw.ac.uz.emhare.admissions.web;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public final class AcademicReviewRequests {
    private AcademicReviewRequests() { }
    public record ReleaseAcademicReviews(
            UUID programmeId, UUID academicUnitId, UUID highestAcademicUnitId, Instant dueAt) { }
    public record ClaimAcademicReview(long expectedVersion) { }
    public record RecordAcademicRecommendation(
            @NotBlank String recommendation, Integer rankPosition, String quotaTypeCode,
            @NotBlank String reason, long expectedVersion) { }
    public record ReviewAcademicRecommendation(
            @NotBlank String action, String finalDecision, @NotBlank String reason) { }
    public record ReleaseWaitlist(@NotBlank String reason) { }
}
