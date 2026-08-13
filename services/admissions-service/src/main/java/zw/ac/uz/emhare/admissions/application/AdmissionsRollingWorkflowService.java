package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendation;
import zw.ac.uz.emhare.admissions.domain.model.AcademicRecommendationReviewStatus;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReview;
import zw.ac.uz.emhare.admissions.domain.model.AcademicReviewStatus;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionRequirementSet;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationEvaluation;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatus;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearance;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationClearanceOutcome;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationSection;
import zw.ac.uz.emhare.admissions.domain.model.QualificationResultStatus;
import zw.ac.uz.emhare.admissions.domain.model.DecisionOutcome;
import zw.ac.uz.emhare.admissions.domain.model.EvaluationStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatusEvent;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceDecision;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceStatus;
import zw.ac.uz.emhare.admissions.domain.model.RecommendationOutcome;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicRecommendationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AcademicReviewRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionRequirementSetRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationEvaluationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationProgrammeChoiceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationClearanceRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicationSectionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ApplicantQualificationSittingRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferStatusEventRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.ProgrammeChoiceDecisionRepository;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;

/** Idempotent applicant-level admissions orchestration required by ADR-0014. @author Tinashe K */
@Service
public class AdmissionsRollingWorkflowService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository choiceRepository;
    private final AdmissionRequirementSetRepository requirementSetRepository;
    private final ApplicationEvaluationRepository evaluationRepository;
    private final AcademicReviewRepository reviewRepository;
    private final AcademicRecommendationRepository recommendationRepository;
    private final ProgrammeChoiceDecisionRepository decisionRepository;
    private final AdmissionOfferRepository offerRepository;
    private final ApplicationStatusEventRepository applicationStatusEventRepository;
    private final OfferStatusEventRepository offerStatusEventRepository;
    private final QualificationEligibilityService eligibilityService;
    private final AcademicSetupCatalogueClient academicSetupCatalogueClient;
    private final AdmissionsIdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AdmissionsDocumentService documentService;
    private final ApplicationSectionRepository sectionRepository;
    private final ApplicantQualificationSittingRepository qualificationRepository;
    private final ApplicationClearanceRepository clearanceRepository;
    private final AdmissionsIntegrationOutboxService outboxService;
    private final ApplicationDuplicateCheckService duplicateCheckService;

    public AdmissionsRollingWorkflowService(
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository choiceRepository,
            AdmissionRequirementSetRepository requirementSetRepository,
            ApplicationEvaluationRepository evaluationRepository,
            AcademicReviewRepository reviewRepository,
            AcademicRecommendationRepository recommendationRepository,
            ProgrammeChoiceDecisionRepository decisionRepository,
            AdmissionOfferRepository offerRepository,
            ApplicationStatusEventRepository applicationStatusEventRepository,
            OfferStatusEventRepository offerStatusEventRepository,
            QualificationEligibilityService eligibilityService,
            AcademicSetupCatalogueClient academicSetupCatalogueClient,
            AdmissionsIdentifierGenerator identifierGenerator,
            ObjectMapper objectMapper,
            Clock clock,
            AdmissionsDocumentService documentService,
            ApplicationSectionRepository sectionRepository,
            ApplicantQualificationSittingRepository qualificationRepository,
            ApplicationClearanceRepository clearanceRepository,
            AdmissionsIntegrationOutboxService outboxService,
            ApplicationDuplicateCheckService duplicateCheckService) {
        this.applicationRepository = applicationRepository;
        this.choiceRepository = choiceRepository;
        this.requirementSetRepository = requirementSetRepository;
        this.evaluationRepository = evaluationRepository;
        this.reviewRepository = reviewRepository;
        this.recommendationRepository = recommendationRepository;
        this.decisionRepository = decisionRepository;
        this.offerRepository = offerRepository;
        this.applicationStatusEventRepository = applicationStatusEventRepository;
        this.offerStatusEventRepository = offerStatusEventRepository;
        this.eligibilityService = eligibilityService;
        this.academicSetupCatalogueClient = academicSetupCatalogueClient;
        this.identifierGenerator = identifierGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.documentService = documentService;
        this.sectionRepository = sectionRepository;
        this.qualificationRepository = qualificationRepository;
        this.clearanceRepository = clearanceRepository;
        this.outboxService = outboxService;
        this.duplicateCheckService = duplicateCheckService;
    }

    @Transactional
    public void advance(UUID applicationId, UUID actorUserId) {
        Application application = application(applicationId);
        if (application.getStatus() == ApplicationStatus.SUBMITTED) {
            ApplicationDuplicateCheckService.DuplicateCheckResult duplicateCheck = duplicateCheckService.check(application);
            if (readyForRollingProcessing(application, duplicateCheck)) {
                ApplicationStatus previous = application.getStatus();
                application.moveToUnderReview(actorUserId, "Application entered rolling admissions processing.");
                if (clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                        applicationId, ApplicationClearanceOutcome.CONFIRMED).isEmpty()) {
                    clearanceRepository.save(new ApplicationClearance(application, actorUserId,
                            "Submission, payment, sections, qualifications, documents and duplicate checks cleared.",
                            duplicateCheck.summary(), clock.instant()));
                }
                recordStatus(application, previous, actorUserId);
                outboxService.enqueueVerificationDecisionNotification(application);
            }
        }
        if (application.getStatus() == ApplicationStatus.UNDER_REVIEW
                || application.getStatus() == ApplicationStatus.ELIGIBLE
                || application.getStatus() == ApplicationStatus.NOT_ELIGIBLE) {
            evaluatePendingChoices(application, actorUserId);
            openHighestEligibleReview(application, actorUserId);
        }
    }

    private boolean readyForRollingProcessing(
            Application application,
            ApplicationDuplicateCheckService.DuplicateCheckResult duplicateCheck) {
        if (!duplicateCheck.passed()) return false;
        if (!application.canEnterReview()) return false;
        if (!documentService.isReadyForReview(application)) return false;
        List<ApplicationSection> requiredSections = sectionRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderBySortOrderAsc(application.getId()).stream()
                .filter(ApplicationSection::isRequired).toList();
        if (requiredSections.stream().anyMatch(section -> !section.isComplete())) return false;
        var qualifications = qualificationRepository
                .findAllByApplicationIdAndDeletedAtIsNullOrderByYearWrittenDesc(application.getId());
        return !qualifications.isEmpty() && qualifications.stream().allMatch(
                sitting -> sitting.getVerificationStatus() == QualificationResultStatus.VERIFIED);
    }

    @Transactional
    public void recalculateEligibility(UUID applicationId, UUID actorUserId) {
        Application application = application(applicationId);
        if (application.getStatus() != ApplicationStatus.UNDER_REVIEW
                && application.getStatus() != ApplicationStatus.ELIGIBLE
                && application.getStatus() != ApplicationStatus.NOT_ELIGIBLE) {
            throw new IllegalStateException("Eligibility can only be recalculated for an application under review.");
        }
        var snapshot = eligibilityService.recalculateApplicationPoints(applicationId);
        application.recordCalculatedPoints(snapshot.totalPoints(), clock.instant());
        evaluatePendingChoices(application, actorUserId);
        openHighestEligibleReview(application, actorUserId);
    }

    @Transactional
    public void resolveEligibility(UUID applicationId, UUID choiceId, String outcomeCode,
            String reason, UUID actorUserId) {
        Application application = application(applicationId);
        ApplicationProgrammeChoice choice = choice(application, choiceId);
        if (choice.getChoiceStatus() != ProgrammeChoiceStatus.REQUIRES_REVIEW) {
            throw new IllegalStateException("Only a programme choice requiring review can be resolved manually.");
        }
        EvaluationStatus outcome = parseEligibilityOutcome(outcomeCode);
        choice.recordEvaluation(outcome, required(reason));
        refreshApplicationEligibility(application, actorUserId, reason);
        if (outcome == EvaluationStatus.ELIGIBLE || outcome == EvaluationStatus.CONDITIONALLY_ELIGIBLE) {
            openHighestEligibleReview(application, actorUserId);
        }
    }

    @Transactional
    public void recommend(UUID applicationId, UUID choiceId, String recommendationCode,
            String reason, CoreCurrentUserProfile profile) {
        Application application = application(applicationId);
        ApplicationProgrammeChoice choice = choice(application, choiceId);
        AcademicReview review = reviewRepository
                .findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, choiceId)
                .orElseThrow(() -> new IllegalStateException("Academic review is not open for this programme choice."));
        requireAcademicUnitScope(profile, review.getRecommendationAcademicUnitId());
        UUID actorUserId = profile.user().id();
        if (review.getStatus() == AcademicReviewStatus.OPEN || review.getStatus() == AcademicReviewStatus.RETURNED) {
            review.claim(actorUserId, clock.instant(), review.getVersion());
        }
        RecommendationOutcome recommendation = parseEnum(
                RecommendationOutcome.class, recommendationCode, "academic recommendation");
        int sequence = recommendationRepository.countByAcademicReviewIdAndDeletedAtIsNull(review.getId()) + 1;
        recommendationRepository.save(new AcademicRecommendation(
                review, sequence, recommendation, required(reason), actorUserId, clock.instant()));
        review.markRecommended(actorUserId, review.getVersion());
        reviewRepository.saveAndFlush(review);
    }

    @Transactional
    public void returnRecommendation(UUID applicationId, UUID choiceId, String reason, UUID actorUserId) {
        Application application = application(applicationId);
        choice(application, choiceId);
        AcademicReview review = reviewRepository
                .findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, choiceId)
                .orElseThrow(() -> new IllegalStateException("Academic review was not found."));
        AcademicRecommendation recommendation = recommendationRepository
                .findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(
                        review.getId(), AcademicRecommendationReviewStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("A pending academic recommendation was not found."));
        String returnReason = required(reason);
        recommendation.returnForReconsideration(actorUserId, returnReason, clock.instant());
        review.returnForReconsideration();
        recommendationRepository.save(recommendation);
        reviewRepository.saveAndFlush(review);
    }

    @Transactional
    public AdmissionOfferSummary decide(UUID applicationId, UUID choiceId, String decisionCode,
            String reason, UUID actorUserId) {
        Application application = application(applicationId);
        ApplicationProgrammeChoice choice = choice(application, choiceId);
        if (decisionRepository.existsByProgrammeChoiceIdAndDeletedAtIsNull(choiceId)) {
            return offerRepository.findByProgrammeChoiceIdAndDeletedAtIsNull(choiceId)
                    .map(offer -> AdmissionOfferSummary.from(offer, List.of(), null))
                    .orElse(null);
        }
        AcademicReview review = reviewRepository
                .findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(applicationId, choiceId)
                .orElseThrow(() -> new IllegalStateException("Academic review was not found."));
        AcademicRecommendation recommendation = recommendationRepository
                .findByAcademicReviewIdAndReviewStatusAndDeletedAtIsNull(
                        review.getId(), AcademicRecommendationReviewStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("An academic recommendation is required before the final decision."));
        DecisionOutcome decision = parseEnum(DecisionOutcome.class, decisionCode, "admission decision");
        if (matches(recommendation.getRecommendation(), decision)) {
            recommendation.approve(actorUserId, reason, clock.instant());
        } else {
            recommendation.override(actorUserId, reason, clock.instant());
        }
        ProgrammeChoiceDecision savedDecision = decisionRepository.saveAndFlush(new ProgrammeChoiceDecision(
                application, choice, decision, required(reason), recommendation, actorUserId, clock.instant()));
        choice.recordDecision(decision, reason);
        review.complete(clock.instant());
        reviewRepository.save(review);
        choiceRepository.saveAndFlush(choice);

        ApplicationStatus previousApplicationStatus = application.getStatus();
        if (decision == DecisionOutcome.ADMIT) {
            application.recordChoiceDecision(decision, reason);
            closeLowerChoices(application, choice);
            AdmissionOffer offer = offerRepository.saveAndFlush(new AdmissionOffer(
                    application, choice, savedDecision,
                    identifierGenerator.nextOfferNumber(application.getIntakeCode())));
            offerStatusEventRepository.save(new OfferStatusEvent(
                    offer, null, OfferStatus.DRAFT, "Draft offer created from direct admission decision.",
                    actorUserId, clock.instant()));
            recordStatus(application, previousApplicationStatus, actorUserId);
            return AdmissionOfferSummary.from(offer, List.of(), null);
        }

        ApplicationProgrammeChoice next = nextEligibleChoice(application, choice.getChoiceRank());
        if (next == null) {
            application.rejectAfterAllChoices("All eligible programme choices were rejected.");
        } else {
            application.continueAfterChoiceRejection("Continuing with the next eligible programme choice.");
            createAcademicReview(next);
            next.enterAcademicReview();
            choiceRepository.saveAndFlush(next);
        }
        recordStatus(application, previousApplicationStatus, actorUserId);
        return null;
    }

    private void evaluatePendingChoices(Application application, UUID actorUserId) {
        List<ApplicationProgrammeChoice> choices = choiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(application.getId());
        for (ApplicationProgrammeChoice choice : choices) {
            if (choice.getChoiceStatus() != ProgrammeChoiceStatus.PENDING
                    && choice.getChoiceStatus() != ProgrammeChoiceStatus.INELIGIBLE
                    && choice.getChoiceStatus() != ProgrammeChoiceStatus.REQUIRES_REVIEW) continue;
            AdmissionRequirementSet requirementSet = applicableRequirementSet(application, choice);
            if (requirementSet == null) {
                choice.recordEvaluation(EvaluationStatus.REQUIRES_REVIEW,
                        "No approved requirement set applies to this programme choice.");
                continue;
            }
            var result = eligibilityService.evaluateRequirements(application, requirementSet);
            EvaluationStatus status = requirementSet.getAdvancedRulesVersion() != null
                    ? EvaluationStatus.REQUIRES_REVIEW
                    : result.missingRequirements().isEmpty()
                            ? EvaluationStatus.ELIGIBLE : EvaluationStatus.NOT_ELIGIBLE;
            choice.recordEvaluation(status, status == EvaluationStatus.ELIGIBLE
                    ? "All configured eligibility requirements are satisfied."
                    : status == EvaluationStatus.REQUIRES_REVIEW
                            ? "Advanced or alternative-entry rules require staff review."
                            : "Configured eligibility requirements are not satisfied: "
                                    + String.join(", ", result.missingRequirements()));
            if (!evaluationRepository.existsByProgrammeChoiceIdAndRequirementSetIdAndDeletedAtIsNull(
                    choice.getId(), requirementSet.getId())) {
                evaluationRepository.save(new ApplicationEvaluation(
                        application, choice, requirementSet, status, result.totalPoints(), null,
                        json(result.missingRequirementEvidence()), json(result.ruleEvidence()),
                        clock.instant(), actorUserId));
            }
        }
        choiceRepository.saveAllAndFlush(choices);
        refreshApplicationEligibility(application, actorUserId, "Eligibility recalculated for rolling processing.");
    }

    private void refreshApplicationEligibility(Application application, UUID actorUserId, String reason) {
        List<ApplicationProgrammeChoice> choices = choiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(application.getId());
        boolean anyEligible = choices.stream().anyMatch(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                || choice.getChoiceStatus() == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE);
        boolean allFinal = choices.stream().allMatch(choice -> choice.getChoiceStatus() != ProgrammeChoiceStatus.PENDING
                && choice.getChoiceStatus() != ProgrammeChoiceStatus.REQUIRES_REVIEW);
        ApplicationStatus previous = application.getStatus();
        application.applyEvaluationOutcome(anyEligible, allFinal, reason);
        if (allFinal && !anyEligible) {
            application.rejectAfterAllChoices("All programme choices were evaluated and none is eligible.");
        }
        recordStatus(application, previous, actorUserId);
    }

    private void openHighestEligibleReview(Application application, UUID actorUserId) {
        if (!reviewRepository.findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(application.getId())
                .stream().filter(review -> review.getStatus() != AcademicReviewStatus.COMPLETED
                        && review.getStatus() != AcademicReviewStatus.CANCELLED).toList().isEmpty()) return;
        ApplicationProgrammeChoice next = nextEligibleChoice(application, 0);
        if (next == null) return;
        createAcademicReview(next);
        next.enterAcademicReview();
        choiceRepository.saveAndFlush(next);
        if (application.getStatus() == ApplicationStatus.ELIGIBLE) {
            ApplicationStatus previous = application.getStatus();
            application.enterAcademicReview("Academic review opened for choice " + next.getChoiceRank() + ".");
            recordStatus(application, previous, actorUserId);
        }
    }

    private AcademicReview createAcademicReview(ApplicationProgrammeChoice choice) {
        return reviewRepository.findByApplicationIdAndProgrammeChoiceIdAndDeletedAtIsNull(
                        choice.getApplication().getId(), choice.getId())
                .orElseGet(() -> {
                    var hierarchy = academicSetupCatalogueClient.getProgrammeHierarchy(choice.getProgrammeId());
                    var owning = hierarchy.owningAcademicUnit();
                    var highest = hierarchy.highestAcademicUnit();
                    return reviewRepository.saveAndFlush(new AcademicReview(
                            choice.getApplication(), choice,
                            owning.id(), owning.code(), owning.name(),
                            highest.id(), highest.code(), highest.name(), json(hierarchy.ancestorPath())));
                });
    }

    private AdmissionRequirementSet applicableRequirementSet(
            Application application, ApplicationProgrammeChoice choice) {
        LocalDate date = LocalDate.now(clock);
        return requirementSetRepository.findApprovedForRouteForUpdate(
                        choice.getProgrammeId(), application.getApplicationType().getId(), application.getIntakeId())
                .stream()
                .filter(set -> set.isApprovedAndEffectiveFor(choice.getProgrammeId(),
                        application.getApplicationType().getId(), application.getIntakeId(), date))
                .max(Comparator.comparing(AdmissionRequirementSet::getEffectiveFrom))
                .orElse(null);
    }

    private ApplicationProgrammeChoice nextEligibleChoice(Application application, int afterRank) {
        return choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(application.getId()).stream()
                .filter(choice -> choice.getChoiceRank() > afterRank)
                .filter(choice -> choice.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE
                        || choice.getChoiceStatus() == ProgrammeChoiceStatus.CONDITIONALLY_ELIGIBLE)
                .findFirst().orElse(null);
    }

    private void closeLowerChoices(Application application, ApplicationProgrammeChoice admittedChoice) {
        List<ApplicationProgrammeChoice> lowerChoices = choiceRepository
                .findAllByApplicationIdOrderByChoiceRankAsc(application.getId()).stream()
                .filter(choice -> choice.getChoiceRank() > admittedChoice.getChoiceRank()).toList();
        lowerChoices.forEach(choice -> choice.closeAfterHigherRankAdmission(
                "Closed because a higher-preference programme choice was admitted."));
        choiceRepository.saveAll(lowerChoices);
    }

    private void recordStatus(Application application, ApplicationStatus previous, UUID actorUserId) {
        if (previous == application.getStatus()) return;
        applicationStatusEventRepository.save(new ApplicationStatusEvent(
                application, previous, application.getStatus(), application.getStatusReason(), actorUserId));
    }

    private void requireAcademicUnitScope(CoreCurrentUserProfile profile, UUID academicUnitId) {
        if (profile == null || profile.user() == null || !"ACTIVE".equals(profile.user().status())
                || profile.roleAssignments() == null || profile.roleAssignments().stream()
                .noneMatch(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode())
                        && academicUnitId.equals(role.academicUnitId()))) {
            throw new AccessDeniedException("An active assignment at the exact academic unit is required.");
        }
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    }

    private ApplicationProgrammeChoice choice(Application application, UUID choiceId) {
        ApplicationProgrammeChoice choice = choiceRepository.findById(choiceId)
                .orElseThrow(() -> new IllegalArgumentException("Programme choice not found."));
        if (!choice.getApplication().getId().equals(application.getId())) {
            throw new IllegalArgumentException("Programme choice does not belong to the application.");
        }
        return choice;
    }

    private EvaluationStatus parseEligibilityOutcome(String value) {
        EvaluationStatus status = parseEnum(EvaluationStatus.class, value, "eligibility outcome");
        if (status != EvaluationStatus.ELIGIBLE && status != EvaluationStatus.CONDITIONALLY_ELIGIBLE
                && status != EvaluationStatus.NOT_ELIGIBLE) {
            throw new IllegalArgumentException("Manual eligibility outcome must be eligible, conditionally eligible, or not eligible.");
        }
        return status;
    }

    private boolean matches(RecommendationOutcome recommendation, DecisionOutcome decision) {
        return recommendation == RecommendationOutcome.RECOMMEND_ADMIT && decision == DecisionOutcome.ADMIT
                || recommendation == RecommendationOutcome.RECOMMEND_REJECT && decision == DecisionOutcome.REJECT;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported " + label + ".", exception);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A reason is required.");
        return value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Admissions evidence could not be serialized.", exception);
        }
    }
}
