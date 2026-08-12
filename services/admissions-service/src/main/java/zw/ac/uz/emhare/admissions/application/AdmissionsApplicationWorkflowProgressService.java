package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationWorkflowProgress.WorkflowStage;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendation;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReview;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearance;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearanceOutcome;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferResponse;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceDecision;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicRecommendationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicReviewRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationClearanceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferResponseRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ProgrammeChoiceDecisionRepository;

/** Builds the six-stage rolling applicant workflow from authoritative case records. @author Tinashe K */
@Service
public class AdmissionsApplicationWorkflowProgressService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationClearanceRepository clearanceRepository;
    private final ApplicationProgrammeChoiceRepository choiceRepository;
    private final AcademicReviewRepository reviewRepository;
    private final AcademicRecommendationRepository recommendationRepository;
    private final ProgrammeChoiceDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final OfferResponseRepository responseRepository;

    public AdmissionsApplicationWorkflowProgressService(
            ApplicationRepository applicationRepository,
            ApplicationClearanceRepository clearanceRepository,
            ApplicationProgrammeChoiceRepository choiceRepository,
            AcademicReviewRepository reviewRepository,
            AcademicRecommendationRepository recommendationRepository,
            ProgrammeChoiceDecisionRepository decisionRepository,
            AdmissionOfferRepository offerRepository,
            OfferResponseRepository responseRepository) {
        this.applicationRepository = applicationRepository;
        this.clearanceRepository = clearanceRepository;
        this.choiceRepository = choiceRepository;
        this.reviewRepository = reviewRepository;
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.responseRepository = responseRepository;
    }

    @Transactional
    public AdmissionsApplicationWorkflowProgress progress(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ApplicationClearance clearance = clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                applicationId, ApplicationClearanceOutcome.CONFIRMED).orElse(null);
        List<ApplicationProgrammeChoice> choices = choiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(applicationId);
        AcademicReview review = reviewRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        AcademicRecommendation recommendation = review == null ? null : recommendationRepository
                .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(review.getId())
                .stream().findFirst().orElse(null);
        ProgrammeChoiceDecision decision = decisionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        AdmissionOffer offer = offerRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        OfferResponse response = offer == null ? null : responseRepository.findByOfferId(offer.getId()).orElse(null);

        List<WorkflowStage> stages = new ArrayList<>();
        stages.add(verificationStage(application, clearance));
        stages.add(eligibilityStage(application, clearance, choices));
        stages.add(academicStage(choices, review, recommendation));
        stages.add(decisionStage(review, recommendation, decision, application));
        stages.add(offerStage(decision, offer, application));
        stages.add(responseStage(offer, response));
        String current = stages.stream().filter(stage -> "CURRENT".equals(stage.state()))
                .map(WorkflowStage::code).findFirst()
                .orElseGet(() -> stages.stream().filter(stage -> "COMPLETED".equals(stage.state()))
                        .reduce((first, second) -> second).map(WorkflowStage::code).orElse("VERIFICATION"));
        return new AdmissionsApplicationWorkflowProgress(current, List.copyOf(stages));
    }

    private WorkflowStage verificationStage(Application application, ApplicationClearance clearance) {
        if (clearance != null || application.getStatus() != ApplicationStatus.DRAFT
                && application.getStatus() != ApplicationStatus.SUBMITTED) {
            return stage(1, "VERIFICATION", "Verification", "COMPLETED", "Checks cleared",
                    "Submission, payment, required sections, qualifications and documents are complete.",
                    clearance == null ? application.getUpdatedAt() : clearance.getConfirmedAt());
        }
        return stage(1, "VERIFICATION", "Verification", "CURRENT",
                application.getStatus() == ApplicationStatus.DRAFT ? "Awaiting submission" : "Checks in progress",
                "Admissions verifies payment, sections, qualifications and required documents.", null);
    }

    private WorkflowStage eligibilityStage(Application application, ApplicationClearance clearance,
            List<ApplicationProgrammeChoice> choices) {
        boolean requiresReview = choices.stream().anyMatch(choice ->
                choice.getChoiceStatus() == ProgrammeChoiceStatus.REQUIRES_REVIEW);
        boolean evaluated = choices.stream().allMatch(choice -> choice.getChoiceStatus() != ProgrammeChoiceStatus.PENDING);
        boolean eligible = choices.stream().anyMatch(this::eligible);
        if (requiresReview) return stage(2, "ELIGIBILITY", "Eligibility", "CURRENT", "Admissions review required",
                "A reasoned eligibility resolution is required for a missing or alternative-entry rule.", application.getPointsCalculatedAt());
        if (evaluated) return stage(2, "ELIGIBILITY", "Eligibility", "COMPLETED",
                eligible ? "Eligible choice found" : "No eligible choice", "Choices were evaluated in applicant preference order.",
                application.getPointsCalculatedAt());
        return stage(2, "ELIGIBILITY", "Eligibility", clearance == null ? "PENDING" : "CURRENT",
                clearance == null ? "Waiting for verification" : "Ready to evaluate",
                "Configured server-side points and requirement rules determine eligibility.", null);
    }

    private WorkflowStage academicStage(List<ApplicationProgrammeChoice> choices, AcademicReview review,
            AcademicRecommendation recommendation) {
        if (recommendation != null) return stage(3, "ACADEMIC_REVIEW", "Academic review", "COMPLETED",
                readable(recommendation.getRecommendation().name()), recommendation.getReason(), recommendation.getRecommendedAt());
        if (review != null) return stage(3, "ACADEMIC_REVIEW", "Academic review", "CURRENT",
                readable(review.getStatus().name()), review.getRecommendationAcademicUnitName(), review.getClaimedAt());
        boolean eligible = choices.stream().anyMatch(this::eligible);
        return stage(3, "ACADEMIC_REVIEW", "Academic review", eligible ? "CURRENT" : "PENDING",
                eligible ? "Ready for academic review" : "Waiting for eligibility",
                "The assigned academic unit records an advisory recommendation.", null);
    }

    private WorkflowStage decisionStage(AcademicReview review, AcademicRecommendation recommendation,
            ProgrammeChoiceDecision decision, Application application) {
        if (decision != null) return stage(4, "ADMISSION_DECISION", "Admission decision", "COMPLETED",
                readable(decision.getDecision().name()), decision.getReason(), decision.getDecidedAt());
        if (application.getStatus() == ApplicationStatus.REJECTED) return stage(4, "ADMISSION_DECISION", "Admission decision",
                "COMPLETED", "Rejected", application.getStatusReason(), application.getUpdatedAt());
        return stage(4, "ADMISSION_DECISION", "Admission decision", recommendation == null ? "PENDING" : "CURRENT",
                recommendation == null ? "Waiting for recommendation" : "Awaiting Admissions decision",
                "Academic recommendations are advisory; Admissions records the final decision.",
                review == null ? null : review.getCompletedAt());
    }

    private WorkflowStage offerStage(ProgrammeChoiceDecision decision, AdmissionOffer offer, Application application) {
        if (offer != null) {
            boolean published = offer.getCurrentPublication() != null;
            return stage(5, "OFFER", "Offer", published ? "COMPLETED" : "CURRENT",
                    offer.isAmendmentPending() ? "Amendment pending" : readable(offer.getStatus().name()),
                    offer.getOfferNumber() + " · " + offer.getProgrammeCode(),
                    published ? offer.getCurrentPublication().getPortalPublishedAt() : offer.getCreatedAt());
        }
        if (application.getStatus() == ApplicationStatus.REJECTED) return stage(5, "OFFER", "Offer", "NOT_APPLICABLE",
                "No offer due", "All programme choices were rejected.", null);
        return stage(5, "OFFER", "Offer", decision == null ? "PENDING" : "CURRENT",
                decision == null ? "Waiting for decision" : "Creating offer draft",
                "An admitted choice creates one direct draft offer without a round or batch.", null);
    }

    private WorkflowStage responseStage(AdmissionOffer offer, OfferResponse response) {
        if (response != null) return stage(6, "RESPONSE", "Response", "COMPLETED",
                readable(response.getResponse().name()), "Response is linked to the exact published letter.", response.getRespondedAt());
        if (offer != null && offer.getCurrentPublication() != null) return stage(6, "RESPONSE", "Response", "CURRENT",
                offer.isAmendmentPending() ? "Blocked by pending amendment" : "Awaiting applicant response",
                "Portal publication remains authoritative even if email delivery fails.", offer.getCurrentPublication().getPortalPublishedAt());
        return stage(6, "RESPONSE", "Response", "PENDING", "Waiting for publication",
                "Accept and decline are available only for the current published letter.", null);
    }

    private boolean eligible(ApplicationProgrammeChoice choice) {
        return choice.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                || choice.getChoiceStatus() == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE
                || choice.getChoiceStatus() == ProgrammeChoiceStatus.UNDER_ACADEMIC_REVIEW
                || choice.getChoiceStatus() == ProgrammeChoiceStatus.ADMITTED;
    }

    private WorkflowStage stage(int sequence, String code, String label, String state,
            String statusLabel, String detail, Instant occurredAt) {
        return new WorkflowStage(sequence, code, label, state, statusLabel, detail, occurredAt);
    }

    private String readable(String value) {
        String normalized = value == null ? "" : value.toLowerCase().replace('_', ' ');
        return normalized.isBlank() ? "Pending" : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
