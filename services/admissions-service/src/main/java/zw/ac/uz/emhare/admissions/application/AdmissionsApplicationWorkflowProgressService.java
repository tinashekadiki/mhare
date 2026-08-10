package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.AdmissionsApplicationWorkflowProgress.WorkflowStage;

/** Builds the five-stage operational workflow position for an application. @author Tinashe K */
@Service
public class AdmissionsApplicationWorkflowProgressService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationClearanceRepository clearanceRepository;
    private final AcademicReviewAssignmentRepository assignmentRepository;
    private final AcademicUnitRecommendationRepository recommendationRepository;
    private final SelectionDecisionRepository selectionDecisionRepository;
    private final AdmissionOfferRepository offerRepository;

    public AdmissionsApplicationWorkflowProgressService(
            ApplicationRepository applicationRepository,
            ApplicationClearanceRepository clearanceRepository,
            AcademicReviewAssignmentRepository assignmentRepository,
            AcademicUnitRecommendationRepository recommendationRepository,
            SelectionDecisionRepository selectionDecisionRepository,
            AdmissionOfferRepository offerRepository) {
        this.applicationRepository = applicationRepository;
        this.clearanceRepository = clearanceRepository;
        this.assignmentRepository = assignmentRepository;
        this.recommendationRepository = recommendationRepository;
        this.selectionDecisionRepository = selectionDecisionRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional
    public AdmissionsApplicationWorkflowProgress progress(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        ApplicationClearance clearance = clearanceRepository
                .findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                        applicationId, ApplicationClearanceOutcome.CONFIRMED)
                .orElse(null);
        AcademicReviewAssignment assignment = assignmentRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByReleasedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        AcademicUnitRecommendation recommendation = assignment == null
                ? null
                : recommendationRepository
                        .findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignment.getId())
                        .stream().findFirst().orElse(null);
        SelectionDecision selectionDecision = assignment == null
                ? selectionDecisionRepository
                        .findAllByProgrammeChoiceApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(applicationId)
                        .stream().findFirst().orElse(null)
                : selectionDecisionRepository
                        .findBySelectionRoundIdAndProgrammeChoiceIdAndDeletedAtIsNull(
                                assignment.getSelectionRound().getId(), assignment.getProgrammeChoice().getId())
                        .orElse(null);
        AdmissionOffer offer = offerRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(candidate -> assignment == null
                        || assignment.getProgrammeChoice().getId().equals(candidate.getProgrammeChoice().getId()))
                .findFirst().orElse(null);

        List<WorkflowStage> stages = new ArrayList<>();
        stages.add(confirmStage(application, clearance));
        stages.add(releaseStage(clearance, assignment));
        stages.add(recommendationStage(assignment, recommendation));
        stages.add(decisionStage(recommendation, selectionDecision));
        stages.add(offerStage(selectionDecision, offer));

        String currentStageCode = stages.stream()
                .filter(stage -> "CURRENT".equals(stage.state()))
                .map(WorkflowStage::code)
                .findFirst()
                .orElseGet(() -> stages.stream()
                        .filter(stage -> "COMPLETED".equals(stage.state()))
                        .reduce((first, second) -> second)
                        .map(WorkflowStage::code)
                        .orElse("CONFIRM"));
        return new AdmissionsApplicationWorkflowProgress(currentStageCode, List.copyOf(stages));
    }

    private WorkflowStage confirmStage(Application application, ApplicationClearance clearance) {
        if (clearance != null) {
            return stage(1, "CONFIRM", "Confirmed by Admissions", "COMPLETED",
                    "Admissions checks completed", "Payment, documents and qualifications cleared.",
                    clearance.getConfirmedAt());
        }
        String statusLabel = application.getStatus() == ApplicationStatus.DRAFT
                ? "Application not submitted"
                : "Awaiting Admissions checks";
        return stage(1, "CONFIRM", "Confirmed by Admissions", "CURRENT",
                statusLabel, "The application must be confirmed before academic release.", null);
    }

    private WorkflowStage releaseStage(
            ApplicationClearance clearance, AcademicReviewAssignment assignment) {
        if (assignment != null) {
            return stage(2, "RELEASE", "Released for recommendation", "COMPLETED",
                    "Released to " + assignment.getRecommendationAcademicUnitName(),
                    assignment.getProgrammeChoice().getProgrammeCode() + " · "
                            + assignment.getProgrammeChoice().getProgrammeName(),
                    assignment.getReleasedAt());
        }
        return stage(2, "RELEASE", "Released for recommendation",
                clearance == null ? "PENDING" : "CURRENT",
                clearance == null ? "Waiting for confirmation" : "Ready for release",
                "Admissions releases an eligible programme choice to the academic unit.", null);
    }

    private WorkflowStage recommendationStage(
            AcademicReviewAssignment assignment, AcademicUnitRecommendation recommendation) {
        if (recommendation != null) {
            return stage(3, "RECOMMEND", "Academic-unit recommendation", "COMPLETED",
                    readable(recommendation.getRecommendation().name()) + " recommended",
                    assignment.getRecommendationAcademicUnitName(), recommendation.getRecommendedAt());
        }
        if (assignment != null) {
            return stage(3, "RECOMMEND", "Academic-unit recommendation", "CURRENT",
                    assignmentStatus(assignment.getStatus()), assignment.getRecommendationAcademicUnitName(),
                    assignment.getClaimedAt());
        }
        return stage(3, "RECOMMEND", "Academic-unit recommendation", "PENDING",
                "Waiting for release", "The academic unit records an advisory recommendation.", null);
    }

    private WorkflowStage decisionStage(
            AcademicUnitRecommendation recommendation, SelectionDecision selectionDecision) {
        if (selectionDecision != null) {
            return stage(4, "DECIDE", "Admissions final decision", "COMPLETED",
                    readable(selectionDecision.getDecision().name()) + " decision",
                    selectionDecision.getProgrammeChoice().getProgrammeCode() + " · "
                            + selectionDecision.getProgrammeChoice().getProgrammeName(),
                    selectionDecision.getDecidedAt());
        }
        if (recommendation != null) {
            String statusLabel = recommendation.getReviewStatus() == AcademicRecommendationReviewStatus.RETURNED
                    ? "Returned for reconsideration"
                    : "Awaiting Admissions decision";
            return stage(4, "DECIDE", "Admissions final decision", "CURRENT",
                    statusLabel, "Admissions retains final selection authority.", recommendation.getReviewedAt());
        }
        return stage(4, "DECIDE", "Admissions final decision", "PENDING",
                "Waiting for recommendation", "No final decision has been recorded.", null);
    }

    private WorkflowStage offerStage(SelectionDecision selectionDecision, AdmissionOffer offer) {
        if (offer != null) {
            boolean issued = switch (offer.getStatus()) {
                case SENT, ACCEPTED, DECLINED, EXPIRED, WITHDRAWN, CONVERTED -> true;
                case DRAFT, APPROVED -> false;
            };
            return stage(5, "OFFER", "Offer letter", issued ? "COMPLETED" : "CURRENT",
                    readable(offer.getStatus().name()),
                    offer.getOfferNumber() + " · " + offer.getProgrammeCode(), offerTimestamp(offer));
        }
        if (selectionDecision != null && selectionDecision.getDecision() == SelectionDecisionType.SELECT) {
            return stage(5, "OFFER", "Offer letter", "CURRENT", "Ready for offer",
                    "Generate and store the governed offer letter.", null);
        }
        if (selectionDecision != null) {
            return stage(5, "OFFER", "Offer letter", "NOT_APPLICABLE", "No offer due",
                    "Only an Admissions-approved Select decision can create an offer.", null);
        }
        return stage(5, "OFFER", "Offer letter", "PENDING", "Waiting for final decision",
                "Offer processing begins only after an approved Select decision.", null);
    }

    private WorkflowStage stage(
            int sequence, String code, String label, String state,
            String statusLabel, String detail, Instant occurredAt) {
        return new WorkflowStage(sequence, code, label, state, statusLabel, detail, occurredAt);
    }

    private String assignmentStatus(AcademicReviewAssignmentStatus status) {
        return switch (status) {
            case OPEN -> "Awaiting academic-unit review";
            case CLAIMED -> "Review in progress";
            case RETURNED -> "Returned for reconsideration";
            case RECOMMENDED -> "Recommendation recorded";
            case COMPLETED -> "Academic review completed";
            case CANCELLED -> "Academic review cancelled";
        };
    }

    private Instant offerTimestamp(AdmissionOffer offer) {
        if (offer.getSentAt() != null) return offer.getSentAt();
        if (offer.getApprovedAt() != null) return offer.getApprovedAt();
        return offer.getCreatedAt();
    }

    private String readable(String value) {
        String normalized = value == null ? "" : value.toLowerCase().replace('_', ' ');
        if (normalized.isBlank()) return "Pending";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
