package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.admissions.application.ApplicantApplicationWorkspaceViews.ApplicationWorkspace;

/** Consolidated rolling-admissions queue and case contracts. @author Tinashe K */
public final class AdmissionsWorkItemViews {
    private AdmissionsWorkItemViews() { }

    public record WorkItemPage(List<WorkItemRow> content, int page, int size,
            long totalElements, int totalPages) { }

    public record WorkItemRow(
            UUID applicationId,
            String applicationNumber,
            String applicantNumber,
            String applicantName,
            UUID intakeId,
            String intakeCode,
            UUID applicationTypeId,
            String applicationTypeName,
            UUID programmeId,
            String programmeCode,
            String programmeName,
            BigDecimal points,
            String paymentState,
            String stage,
            String outcome,
            List<String> blockers,
            Instant lastActivityAt) { }

    public record WorkItemCase(
            ApplicationWorkspace workspace,
            AcademicReviewView academicReview,
            AcademicRecommendationView academicRecommendation,
            AdmissionDecisionView admissionDecision,
            AdmissionOfferSummary offer,
            List<OfferDocumentVersionView> documentVersions,
            List<OfferPublicationView> publications,
            List<AuditEventView> auditHistory,
            List<String> blockers,
            List<String> availableActions) { }

    public record AcademicReviewView(UUID id, UUID programmeChoiceId, String status,
            UUID recommendationAcademicUnitId, String recommendationAcademicUnitName,
            UUID claimedByUserId, Instant claimedAt, Instant completedAt, long version) { }

    public record AcademicRecommendationView(UUID id, String recommendation, String reason,
            UUID recommendedByUserId, Instant recommendedAt, String reviewStatus) { }

    public record AdmissionDecisionView(UUID id, String decision, String reason,
            UUID decidedByUserId, Instant decidedAt) { }

    public record OfferDocumentVersionView(UUID id, int version, String status, UUID generatedDocumentId,
            String documentNumber, String checksumSha256, Instant requestedAt, Instant storedAt, String failureReason) { }

    public record OfferPublicationView(UUID id, UUID documentVersionId, int sequence, Instant portalPublishedAt,
            UUID publishedByUserId, String emailStatus, Instant emailStatusAt, String emailFailureReason,
            boolean current, Instant supersededAt) { }

    public record AuditEventView(UUID id, String fromStatus, String toStatus, String reason,
            UUID changedByUserId, Instant changedAt) { }
}
