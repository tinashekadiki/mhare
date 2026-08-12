package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.application.AdmissionsWorkItemViews.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.*;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;

/** Server-paginated rolling Admissions queue and complete case view. @author Tinashe K */
@Service
public class AdmissionsWorkItemService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository choiceRepository;
    private final AcademicReviewRepository reviewRepository;
    private final AcademicRecommendationRepository recommendationRepository;
    private final ProgrammeChoiceDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final OfferConditionRepository conditionRepository;
    private final OfferResponseRepository responseRepository;
    private final OfferDocumentVersionRepository documentVersionRepository;
    private final OfferPublicationRepository publicationRepository;
    private final ApplicationStatusEventRepository statusEventRepository;
    private final ApplicantApplicationWorkspaceService workspaceService;
    private final AdmissionsApplicationWorkflowProgressService progressService;

    public AdmissionsWorkItemService(
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository choiceRepository,
            AcademicReviewRepository reviewRepository,
            AcademicRecommendationRepository recommendationRepository,
            ProgrammeChoiceDecisionRepository decisionRepository,
            AdmissionOfferRepository offerRepository,
            OfferConditionRepository conditionRepository,
            OfferResponseRepository responseRepository,
            OfferDocumentVersionRepository documentVersionRepository,
            OfferPublicationRepository publicationRepository,
            ApplicationStatusEventRepository statusEventRepository,
            ApplicantApplicationWorkspaceService workspaceService,
            AdmissionsApplicationWorkflowProgressService progressService) {
        this.applicationRepository = applicationRepository;
        this.choiceRepository = choiceRepository;
        this.reviewRepository = reviewRepository;
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.conditionRepository = conditionRepository;
        this.responseRepository = responseRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.publicationRepository = publicationRepository;
        this.statusEventRepository = statusEventRepository;
        this.workspaceService = workspaceService;
        this.progressService = progressService;
    }

    @Transactional
    public WorkItemPage list(String search, String stage, UUID intakeId, UUID applicationTypeId,
            UUID programmeId, String outcome, int page, int size, CoreCurrentUserProfile profile) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Specification<Application> specification = Specification.where(notDeleted())
                .and(search(search)).and(equalsId("admissionCycle", "intakeId", intakeId))
                .and(equalsId("applicationType", "id", applicationTypeId))
                .and(programme(programmeId)).and(outcome(outcome)).and(stage(stage)).and(scope(profile));
        var result = applicationRepository.findAll(specification,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new WorkItemPage(result.stream().map(this::row).toList(), safePage, safeSize,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public WorkItemCase get(UUID applicationId, CoreCurrentUserProfile profile) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        AcademicReview review = reviewRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        requireCaseScope(profile, review);
        AcademicRecommendation recommendation = review == null ? null : recommendationRepository
                .findAllByAcademicReviewIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(review.getId())
                .stream().findFirst().orElse(null);
        ProgrammeChoiceDecision decision = decisionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        AdmissionOffer offer = offerRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(applicationId)
                .stream().findFirst().orElse(null);
        AdmissionOfferSummary offerSummary = offer == null ? null : AdmissionOfferSummary.from(offer,
                conditionRepository.findAllByOfferIdAndDeletedAtIsNullOrderByConditionCodeAsc(offer.getId()),
                responseRepository.findByOfferId(offer.getId()).orElse(null));
        List<OfferDocumentVersionView> documents = offer == null ? List.of() : documentVersionRepository
                .findAllByOfferIdAndDeletedAtIsNullOrderByDocumentVersionDesc(offer.getId()).stream()
                .map(document -> new OfferDocumentVersionView(document.getId(), document.getDocumentVersion(),
                        document.getStatus().name(), document.getGeneratedDocumentId(), document.getDocumentNumber(),
                        document.getChecksumSha256(), document.getRequestedAt(), document.getStoredAt(), document.getFailureReason()))
                .toList();
        List<OfferPublicationView> publications = offer == null ? List.of() : publicationRepository
                .findAllByOfferIdAndDeletedAtIsNullOrderByPublicationSequenceDesc(offer.getId()).stream()
                .map(publication -> new OfferPublicationView(publication.getId(), publication.getDocumentVersion().getId(),
                        publication.getPublicationSequence(), publication.getPortalPublishedAt(), publication.getPublishedByUserId(),
                        publication.getEmailDeliveryStatus().name(), publication.getEmailStatusAt(),
                        publication.getEmailFailureReason(), publication.isCurrentPublication(), publication.getSupersededAt()))
                .toList();
        List<AuditEventView> audit = statusEventRepository.findAllByApplicationIdOrderByChangedAtDesc(applicationId)
                .stream().map(event -> new AuditEventView(event.getId(),
                        event.getFromStatus() == null ? null : event.getFromStatus().name(), event.getToStatus().name(),
                        event.getReason(), event.getChangedByUserId(), event.getChangedAt())).toList();
        return new WorkItemCase(workspaceService.staffWorkspace(applicationId), reviewView(review),
                recommendationView(recommendation), decisionView(decision), offerSummary, documents, publications, audit,
                blockers(application, offer), availableActions(application, review, recommendation, offer, profile));
    }

    private WorkItemRow row(Application application) {
        ApplicationProgrammeChoice choice = activeChoice(application.getId());
        AdmissionOffer offer = offerRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId())
                .stream().findFirst().orElse(null);
        String currentStage = progressService.progress(application.getId()).currentStageCode();
        return new WorkItemRow(application.getId(), application.getApplicationNumber(),
                application.getApplicant().getApplicantNumber(), application.getApplicant().getDisplayName(),
                application.getAdmissionCycle().getIntakeId(), application.getAdmissionCycle().getCode(),
                application.getApplicationType().getId(), application.getApplicationType().getName(),
                choice == null ? null : choice.getProgrammeId(), choice == null ? null : choice.getProgrammeCode(),
                choice == null ? null : choice.getProgrammeName(), application.getCalculatedTotalPoints(),
                paymentState(application), currentStage, outcome(application, offer), blockers(application, offer),
                application.getUpdatedAt());
    }

    private ApplicationProgrammeChoice activeChoice(UUID applicationId) {
        List<ApplicationProgrammeChoice> choices = choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId);
        return choices.stream().filter(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.ADMITTED
                        || choice.getChoiceStatus() == ProgrammeChoiceStatus.OFFERED
                        || choice.getChoiceStatus() == ProgrammeChoiceStatus.CONVERTED)
                .findFirst().orElseGet(() -> choices.stream()
                        .filter(choice -> choice.getChoiceStatus() != ProgrammeChoiceStatus.REJECTED).findFirst()
                        .orElse(choices.isEmpty() ? null : choices.get(0)));
    }

    private List<String> blockers(Application application, AdmissionOffer offer) {
        List<String> blockers = new ArrayList<>();
        if (application.getStatus() == ApplicationStatus.DRAFT) blockers.add("Application has not been submitted.");
        if (application.getApplicationType().getFinanceFeeStructureId() != null
                && application.getPaymentConfirmedAt() == null && application.getPaymentOverrideByUserId() == null) {
            blockers.add("Application fee is not confirmed or waived.");
        }
        if (application.getStatus() == ApplicationStatus.INCOMPLETE) blockers.add("Applicant correction is required.");
        if (choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()).stream()
                .anyMatch(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.REQUIRES_REVIEW)) {
            blockers.add("At least one programme choice requires a reasoned eligibility resolution.");
        }
        if (offer != null && offer.isAmendmentPending()) blockers.add("The replacement offer letter must be published before response.");
        return List.copyOf(blockers);
    }

    private List<String> availableActions(Application application, AcademicReview review,
            AcademicRecommendation recommendation, AdmissionOffer offer, CoreCurrentUserProfile profile) {
        Set<String> permissions = profile.effectivePermissionCodes() == null ? Set.of() : Set.copyOf(profile.effectivePermissionCodes());
        List<String> actions = new ArrayList<>();
        if (permissions.contains("ADMISSIONS_ELIGIBILITY_REVIEW")) {
            actions.add("RECALCULATE_ELIGIBILITY");
            if (choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()).stream()
                    .anyMatch(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.REQUIRES_REVIEW)) {
                actions.add("RESOLVE_ELIGIBILITY");
            }
        }
        if (review != null && isAcademicReviewer(profile, review) && recommendation == null) actions.add("RECORD_ACADEMIC_RECOMMENDATION");
        if (permissions.contains("ADMISSIONS_DECISION_MAKE") && recommendation != null
                && decisionRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByDecidedAtDesc(application.getId()).isEmpty()) {
            actions.add("RECORD_ADMISSION_DECISION");
        }
        if (permissions.contains("ADMISSIONS_OFFER_MANAGE") && offer != null
                && (offer.getStatus() == OfferStatus.DRAFT || offer.getStatus() == OfferStatus.SENT)) {
            actions.add("UPDATE_OFFER");
            actions.add("GENERATE_OFFER_DOCUMENT");
            if (offer.getCurrentDocumentVersion() != null && offer.getOfferType() != null
                    && offer.getAcceptanceDeadline() != null && offer.getCommencementDate() != null) actions.add("PUBLISH_AND_SEND");
            if (offer.getCurrentPublication() != null && (offer.getCurrentPublication().getEmailDeliveryStatus() == OfferEmailDeliveryStatus.FAILED
                    || offer.getCurrentPublication().getEmailDeliveryStatus() == OfferEmailDeliveryStatus.BOUNCED)) actions.add("RETRY_EMAIL");
        }
        return List.copyOf(actions);
    }

    private void requireCaseScope(CoreCurrentUserProfile profile, AcademicReview review) {
        if (profile.effectivePermissionCodes() != null
                && profile.effectivePermissionCodes().contains("ADMISSIONS_APPLICATION_REVIEW")) return;
        if (review == null || !isAcademicReviewer(profile, review)) {
            throw new AccessDeniedException("The application is outside the assigned academic unit.");
        }
    }

    private boolean isAcademicReviewer(CoreCurrentUserProfile profile, AcademicReview review) {
        return profile.roleAssignments() != null && profile.roleAssignments().stream()
                .anyMatch(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode())
                        && review.getRecommendationAcademicUnitId().equals(role.academicUnitId()));
    }

    private AcademicReviewView reviewView(AcademicReview review) {
        return review == null ? null : new AcademicReviewView(review.getId(), review.getProgrammeChoice().getId(),
                review.getStatus().name(), review.getRecommendationAcademicUnitId(), review.getRecommendationAcademicUnitName(),
                review.getClaimedByUserId(), review.getClaimedAt(), review.getCompletedAt(), review.getVersion());
    }

    private AcademicRecommendationView recommendationView(AcademicRecommendation recommendation) {
        return recommendation == null ? null : new AcademicRecommendationView(recommendation.getId(),
                recommendation.getRecommendation().name(), recommendation.getReason(), recommendation.getRecommendedByUserId(),
                recommendation.getRecommendedAt(), recommendation.getReviewStatus().name());
    }

    private AdmissionDecisionView decisionView(ProgrammeChoiceDecision decision) {
        return decision == null ? null : new AdmissionDecisionView(decision.getId(), decision.getDecision().name(),
                decision.getReason(), decision.getDecidedByUserId(), decision.getDecidedAt());
    }

    private String paymentState(Application application) {
        if (application.getApplicationType().getFinanceFeeStructureId() == null) return "NOT_REQUIRED";
        if (application.getPaymentConfirmedAt() != null) return "PAID";
        if (application.getPaymentOverrideByUserId() != null) return "WAIVED";
        return "PENDING";
    }

    private String outcome(Application application, AdmissionOffer offer) {
        return offer == null ? application.getStatus().name() : offer.getStatus().name();
    }

    private Specification<Application> notDeleted() {
        return (root, query, builder) -> builder.isNull(root.get("deletedAt"));
    }

    private Specification<Application> search(String value) {
        if (value == null || value.isBlank()) return unrestricted();
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> {
            var applicant = root.join("applicant", JoinType.INNER);
            var displayName = builder.concat(builder.concat(applicant.get("firstName"), " "), applicant.get("lastName"));
            return builder.or(builder.like(builder.lower(root.get("applicationNumber")), pattern),
                    builder.like(builder.lower(applicant.get("applicantNumber")), pattern),
                    builder.like(builder.lower(displayName), pattern),
                    builder.like(builder.lower(applicant.get("primaryEmail")), pattern));
        };
    }

    private Specification<Application> equalsId(String relation, String property, UUID value) {
        return value == null ? unrestricted()
                : (root, query, builder) -> builder.equal(root.get(relation).get(property), value);
    }

    private Specification<Application> programme(UUID programmeId) {
        if (programmeId == null) return unrestricted();
        return (root, query, builder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var choice = subquery.from(ApplicationProgrammeChoice.class);
            subquery.select(choice.get("application").get("id")).where(
                    builder.equal(choice.get("programmeId"), programmeId), builder.isNull(choice.get("deletedAt")));
            return root.get("id").in(subquery);
        };
    }

    private Specification<Application> outcome(String value) {
        if (value == null || value.isBlank()) return unrestricted();
        try {
            ApplicationStatus status = ApplicationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return (root, query, builder) -> builder.equal(root.get("status"), status);
        } catch (IllegalArgumentException ignored) {
            return (root, query, builder) -> builder.disjunction();
        }
    }

    private Specification<Application> stage(String value) {
        if (value == null || value.isBlank()) return unrestricted();
        EnumSet<ApplicationStatus> statuses = switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "VERIFICATION" -> EnumSet.of(ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED,
                    ApplicationStatus.PAYMENT_PENDING, ApplicationStatus.INCOMPLETE);
            case "ELIGIBILITY" -> EnumSet.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.ELIGIBLE, ApplicationStatus.NOT_ELIGIBLE);
            case "ACADEMIC_REVIEW" -> EnumSet.of(ApplicationStatus.UNDER_ACADEMIC_REVIEW);
            case "ADMISSION_DECISION" -> EnumSet.of(ApplicationStatus.UNDER_ACADEMIC_REVIEW, ApplicationStatus.ADMITTED, ApplicationStatus.REJECTED);
            case "OFFER" -> EnumSet.of(ApplicationStatus.ADMITTED, ApplicationStatus.OFFERED);
            case "RESPONSE" -> EnumSet.of(ApplicationStatus.OFFERED, ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED, ApplicationStatus.CONVERTED);
            default -> EnumSet.noneOf(ApplicationStatus.class);
        };
        return (root, query, builder) -> statuses.isEmpty() ? builder.disjunction() : root.get("status").in(statuses);
    }

    private Specification<Application> scope(CoreCurrentUserProfile profile) {
        if (profile.effectivePermissionCodes() != null
                && profile.effectivePermissionCodes().contains("ADMISSIONS_APPLICATION_REVIEW")) return unrestricted();
        List<UUID> unitIds = profile.roleAssignments() == null ? List.of() : profile.roleAssignments().stream()
                .filter(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode()) && role.academicUnitId() != null)
                .map(role -> role.academicUnitId()).distinct().toList();
        if (unitIds.isEmpty()) return (root, query, builder) -> builder.disjunction();
        return (root, query, builder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var review = subquery.from(AcademicReview.class);
            subquery.select(review.get("application").get("id")).where(
                    review.get("recommendationAcademicUnitId").in(unitIds), builder.isNull(review.get("deletedAt")));
            return root.get("id").in(subquery);
        };
    }

    private Specification<Application> unrestricted() {
        return (root, query, builder) -> builder.conjunction();
    }
}
