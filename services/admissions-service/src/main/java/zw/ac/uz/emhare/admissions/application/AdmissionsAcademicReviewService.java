package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.ProgrammeHierarchyResolution;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.application.AcademicReviewBatchPreview.AcademicUnitBatch;
import zw.ac.uz.emhare.admissions.application.AcademicReviewBatchPreview.ProgrammeBatch;

/** Governs release, exact-root authorisation, recommendation, and Admissions review. @author Tinashe K */
@Service
public class AdmissionsAcademicReviewService {
    private static final List<AcademicReviewAssignmentStatus> ACTIVE_STATUSES = List.of(
            AcademicReviewAssignmentStatus.OPEN, AcademicReviewAssignmentStatus.CLAIMED,
            AcademicReviewAssignmentStatus.RECOMMENDED, AcademicReviewAssignmentStatus.RETURNED);

    private final SelectionRoundRepository selectionRoundRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationProgrammeChoiceRepository choiceRepository;
    private final ApplicationClearanceRepository clearanceRepository;
    private final AcademicReviewAssignmentRepository assignmentRepository;
    private final AcademicUnitRecommendationRepository recommendationRepository;
    private final AcademicSetupCatalogueClient academicSetupClient;
    private final AdmissionsSelectionOfferService selectionOfferService;
    private final AdmissionsIntegrationOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdmissionsAcademicReviewService(
            SelectionRoundRepository selectionRoundRepository,
            ApplicationRepository applicationRepository,
            ApplicationProgrammeChoiceRepository choiceRepository,
            ApplicationClearanceRepository clearanceRepository,
            AcademicReviewAssignmentRepository assignmentRepository,
            AcademicUnitRecommendationRepository recommendationRepository,
            AcademicSetupCatalogueClient academicSetupClient,
            AdmissionsSelectionOfferService selectionOfferService,
            AdmissionsIntegrationOutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.selectionRoundRepository = selectionRoundRepository;
        this.applicationRepository = applicationRepository;
        this.choiceRepository = choiceRepository;
        this.clearanceRepository = clearanceRepository;
        this.assignmentRepository = assignmentRepository;
        this.recommendationRepository = recommendationRepository;
        this.academicSetupClient = academicSetupClient;
        this.selectionOfferService = selectionOfferService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public List<AcademicReviewSummary> releaseEligibleChoices(
            UUID selectionRoundId, UUID programmeId, UUID academicUnitId, UUID highestAcademicUnitId,
            Instant dueAt, UUID actorUserId) {
        SelectionRound round = requireOpenRound(selectionRoundId);
        List<AcademicReviewSummary> released = new ArrayList<>();
        for (ReviewCandidate candidate : releaseCandidates(round)) {
            ApplicationProgrammeChoice choice = candidate.choice();
            ProgrammeHierarchyResolution hierarchy = candidate.hierarchy();
            if (programmeId != null && !programmeId.equals(choice.getProgrammeId())) continue;
            if (academicUnitId != null && hierarchy.ancestorPath().stream()
                    .noneMatch(unit -> academicUnitId.equals(unit.id()))) continue;
            if (highestAcademicUnitId != null
                    && !highestAcademicUnitId.equals(hierarchy.highestAcademicUnit().id())) continue;
            released.add(createAssignment(round, choice, hierarchy, dueAt, actorUserId));
        }
        if (released.isEmpty()) {
            throw new IllegalStateException("No confirmed application has an eligible, unblocked programme choice for this release.");
        }
        return released;
    }

    @Transactional
    public AcademicReviewBatchPreview previewReleaseBatches(UUID selectionRoundId) {
        SelectionRound round = requireOpenRound(selectionRoundId);
        List<ReviewCandidate> candidates = releaseCandidates(round);
        Map<UUID, ProgrammeBatchAccumulator> programmes = new LinkedHashMap<>();
        Map<UUID, AcademicUnitBatchAccumulator> academicUnits = new LinkedHashMap<>();
        Map<UUID, ProgrammeHierarchyResolution> hierarchyByProgramme = new LinkedHashMap<>();
        Set<UUID> eligibleChoiceIds = candidates.stream()
                .map(candidate -> candidate.choice().getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> applicantIds = new java.util.LinkedHashSet<>();

        for (Application application : applicationRepository.findByAdmissionCycleId(round.getAdmissionCycle().getId())) {
            if (!isVisibleInReleaseWorkspace(application.getStatus())) continue;
            List<ApplicationProgrammeChoice> choices = choiceRepository
                    .findAllByApplicationIdOrderByChoiceRankAsc(application.getId());
            if (choices.isEmpty()) continue;
            applicantIds.add(application.getId());
            for (ApplicationProgrammeChoice choice : choices) {
                ProgrammeHierarchyResolution hierarchy = hierarchyByProgramme.computeIfAbsent(
                        choice.getProgrammeId(), academicSetupClient::getProgrammeHierarchy);
                boolean eligibleForRelease = eligibleChoiceIds.contains(choice.getId());
                programmes.computeIfAbsent(choice.getProgrammeId(), ignored -> new ProgrammeBatchAccumulator(
                                choice, hierarchy))
                        .include(application.getId(), eligibleForRelease);
                for (var unit : hierarchy.ancestorPath()) {
                    academicUnits.computeIfAbsent(unit.id(), ignored -> new AcademicUnitBatchAccumulator(unit))
                            .include(application.getId(), eligibleForRelease);
                }
            }
        }

        List<ProgrammeBatch> programmeBatches = programmes.values().stream()
                .map(ProgrammeBatchAccumulator::toBatch)
                .sorted(java.util.Comparator.comparing(ProgrammeBatch::programmeCode))
                .toList();
        List<AcademicUnitBatch> academicUnitBatches = academicUnits.values().stream()
                .map(AcademicUnitBatchAccumulator::toBatch)
                .sorted(java.util.Comparator.comparing(AcademicUnitBatch::academicUnitCode))
                .toList();
        return new AcademicReviewBatchPreview(
                applicantIds.size(),
                candidates.size(),
                programmeBatches,
                academicUnitBatches);
    }

    @Transactional
    public List<AcademicReviewSummary> listAssignments() {
        return assignmentRepository.findAllByDeletedAtIsNullOrderByReleasedAtDesc().stream().map(this::summary).toList();
    }

    @Transactional
    public List<AcademicReviewSummary> listMyAssignments(CoreCurrentUserProfile profile) {
        List<UUID> rootUnitIds = qualifyingRootUnitIds(profile);
        return rootUnitIds.stream()
                .flatMap(root -> assignmentRepository
                        .findAllByRecommendationAcademicUnitIdAndStatusInAndDeletedAtIsNullOrderByDueAtAscReleasedAtAsc(
                                root, ACTIVE_STATUSES).stream())
                .distinct().map(this::summary).toList();
    }

    @Transactional
    public UUID applicationIdForScopedAcademicReviewer(
            UUID assignmentId, CoreCurrentUserProfile profile) {
        AcademicReviewAssignment assignment = assignment(assignmentId);
        requireExactRootAssignment(profile, assignment.getRecommendationAcademicUnitId());
        return assignment.getApplication().getId();
    }

    @Transactional
    public AcademicReviewSummary claim(UUID assignmentId, long expectedVersion, CoreCurrentUserProfile profile) {
        AcademicReviewAssignment assignment = assignment(assignmentId);
        requireExactRootAssignment(profile, assignment.getRecommendationAcademicUnitId());
        assignment.claim(profile.user().id(), clock.instant(), expectedVersion);
        return summary(assignmentRepository.saveAndFlush(assignment));
    }

    @Transactional
    public AcademicReviewSummary recommend(UUID assignmentId, String outcome, Integer rankPosition,
            String quotaTypeCode, String reason, long expectedVersion, CoreCurrentUserProfile profile) {
        AcademicReviewAssignment assignment = assignment(assignmentId);
        requireExactRootAssignment(profile, assignment.getRecommendationAcademicUnitId());
        SelectionDecisionType recommendation = parseDecision(outcome);
        Instant now = clock.instant();
        assignment.markRecommended(profile.user().id(), now, expectedVersion);
        int sequence = Math.toIntExact(recommendationRepository.countByAssignmentIdAndDeletedAtIsNull(assignmentId) + 1);
        AcademicUnitRecommendation saved = recommendationRepository.saveAndFlush(new AcademicUnitRecommendation(
                assignment, sequence, recommendation, rankPosition, quotaTypeCode, reason, profile.user().id(), now));
        assignmentRepository.saveAndFlush(assignment);
        outboxService.enqueueAcademicRecommendationRecorded(assignment, saved);
        return summary(assignment);
    }

    @Transactional
    public AcademicReviewSummary reviewRecommendation(UUID assignmentId, String actionCode,
            String finalDecisionCode, String reason, UUID actorUserId) {
        AcademicReviewAssignment assignment = assignment(assignmentId);
        AcademicUnitRecommendation recommendation = recommendationRepository
                .findByAssignmentIdAndReviewStatusAndDeletedAtIsNull(
                        assignmentId, AcademicRecommendationReviewStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("No pending academic-unit recommendation exists."));
        String action = actionCode == null ? "" : actionCode.trim().toUpperCase(Locale.ROOT);
        if ("RETURN".equals(action)) {
            recommendation.returnForReconsideration(actorUserId, reason, clock.instant());
            assignment.returnForReconsideration();
            outboxService.enqueueAcademicReviewReleased(assignment);
            return summary(assignment);
        }
        SelectionDecisionType finalDecision;
        if ("APPROVE".equals(action)) {
            recommendation.approve(actorUserId, reason, clock.instant());
            finalDecision = recommendation.getRecommendation();
        } else if ("OVERRIDE".equals(action)) {
            finalDecision = parseDecision(finalDecisionCode);
            recommendation.override(finalDecision, actorUserId, reason, clock.instant());
        } else {
            throw new IllegalArgumentException("Review action must be APPROVE, RETURN, or OVERRIDE.");
        }
        selectionOfferService.recordSelectionDecision(
                assignment.getSelectionRound().getId(), assignment.getProgrammeChoice().getId(), finalDecision.name(),
                recommendation.getRankPosition(), recommendation.getQuotaTypeCode(), reason, actorUserId);
        assignment.complete(actorUserId, clock.instant());
        if (finalDecision == SelectionDecisionType.REJECT) {
            releaseNextChoiceIfAvailable(assignment, actorUserId);
        }
        return summary(assignment);
    }

    @Transactional
    public AcademicReviewSummary releaseWaitlist(UUID assignmentId, String reason, UUID actorUserId) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Waitlist release reason is required.");
        AcademicReviewAssignment assignment = assignment(assignmentId);
        AcademicUnitRecommendation recommendation = recommendationRepository
                .findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignmentId)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Academic recommendation was not found."));
        if (assignment.getStatus() != AcademicReviewAssignmentStatus.COMPLETED
                || recommendation.getFinalDecision() != SelectionDecisionType.WAITLIST) {
            throw new IllegalStateException("Only a completed Admissions-approved waitlist can be released.");
        }
        assignment.getProgrammeChoice().releaseWaitlist(reason.trim());
        choiceRepository.saveAndFlush(assignment.getProgrammeChoice());
        releaseNextChoiceIfAvailable(assignment, actorUserId);
        return summary(assignment);
    }

    private void releaseNextChoiceIfAvailable(AcademicReviewAssignment completed, UUID actorUserId) {
        ApplicationProgrammeChoice next = nextReleasableChoice(completed.getApplication().getId());
        if (next == null) return;
        createAssignment(completed.getSelectionRound(), next,
                academicSetupClient.getProgrammeHierarchy(next.getProgrammeId()), completed.getDueAt(), actorUserId);
    }

    private AcademicReviewSummary createAssignment(SelectionRound round, ApplicationProgrammeChoice choice,
            ProgrammeHierarchyResolution hierarchy, Instant dueAt, UUID actorUserId) {
        if (assignmentRepository.findBySelectionRoundIdAndProgrammeChoiceIdAndStatusInAndDeletedAtIsNull(
                round.getId(), choice.getId(), ACTIVE_STATUSES).isPresent()) {
            throw new IllegalStateException("Programme choice already has an active academic review assignment.");
        }
        int attempt = Math.toIntExact(assignmentRepository.countBySelectionRoundIdAndProgrammeChoiceId(
                round.getId(), choice.getId()) + 1);
        AcademicReviewAssignment assignment = assignmentRepository.saveAndFlush(new AcademicReviewAssignment(
                round, choice, hierarchy.owningAcademicUnit().id(), hierarchy.owningAcademicUnit().code(),
                hierarchy.owningAcademicUnit().name(), hierarchy.highestAcademicUnit().id(),
                hierarchy.highestAcademicUnit().code(), hierarchy.highestAcademicUnit().name(),
                serialize(hierarchy.ancestorPath()), attempt, actorUserId, clock.instant(), dueAt));
        outboxService.enqueueAcademicReviewReleased(assignment);
        return summary(assignment);
    }

    private List<ReviewCandidate> releaseCandidates(SelectionRound round) {
        Map<UUID, ProgrammeHierarchyResolution> hierarchyByProgramme = new LinkedHashMap<>();
        List<ReviewCandidate> candidates = new ArrayList<>();
        for (Application application : applicationRepository.findByAdmissionCycleId(round.getAdmissionCycle().getId())) {
            if (clearanceRepository.findByApplicationIdAndOutcomeAndDeletedAtIsNull(
                    application.getId(), ApplicationClearanceOutcome.CONFIRMED).isEmpty()) continue;
            ApplicationProgrammeChoice choice = nextReleasableChoice(application.getId());
            if (choice == null || assignmentRepository
                    .findBySelectionRoundIdAndProgrammeChoiceIdAndStatusInAndDeletedAtIsNull(
                            round.getId(), choice.getId(), ACTIVE_STATUSES).isPresent()) continue;
            ProgrammeHierarchyResolution hierarchy = hierarchyByProgramme.computeIfAbsent(
                    choice.getProgrammeId(), academicSetupClient::getProgrammeHierarchy);
            candidates.add(new ReviewCandidate(choice, hierarchy));
        }
        return candidates;
    }

    private ApplicationProgrammeChoice nextReleasableChoice(UUID applicationId) {
        List<ApplicationProgrammeChoice> choices = choiceRepository.findAllByApplicationIdOrderByChoiceRankAsc(applicationId);
        for (ApplicationProgrammeChoice choice : choices) {
            if (choice.getChoiceStatus() == ProgrammeChoiceStatus.ELIGIBLE) return choice;
            if (choice.getChoiceStatus() != ProgrammeChoiceStatus.INELIGIBLE
                    && choice.getChoiceStatus() != ProgrammeChoiceStatus.REJECTED) return null;
        }
        return null;
    }

    private boolean isVisibleInReleaseWorkspace(ApplicationStatus status) {
        return status != null
                && status != ApplicationStatus.DRAFT
                && status != ApplicationStatus.WITHDRAWN
                && status != ApplicationStatus.DECLINED
                && status != ApplicationStatus.CONVERTED;
    }

    private List<UUID> qualifyingRootUnitIds(CoreCurrentUserProfile profile) {
        if (profile == null || profile.user() == null || !"ACTIVE".equals(profile.user().status())
                || profile.roleAssignments() == null) {
            return List.of();
        }
        return profile.roleAssignments().stream()
                .filter(role -> "ACADEMIC_UNIT_STAFF".equals(role.roleCode()))
                .map(role -> role.academicUnitId()).filter(java.util.Objects::nonNull).distinct().toList();
    }
    private void requireExactRootAssignment(CoreCurrentUserProfile profile, UUID rootUnitId) {
        if (!qualifyingRootUnitIds(profile).contains(rootUnitId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "An active Academic Unit Staff assignment at the exact highest academic unit is required.");
        }
    }
    private SelectionRound requireOpenRound(UUID id) {
        SelectionRound round = selectionRoundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Selection round not found."));
        if (round.getStatus() != SelectionRoundStatus.OPEN) {
            throw new IllegalStateException("Academic reviews can only be released in an open selection round.");
        }
        return round;
    }
    private AcademicReviewAssignment assignment(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Academic review assignment not found."));
    }
    private AcademicReviewSummary summary(AcademicReviewAssignment assignment) {
        AcademicUnitRecommendation recommendation = recommendationRepository
                .findAllByAssignmentIdAndDeletedAtIsNullOrderByRecommendationSequenceDesc(assignment.getId())
                .stream().findFirst().orElse(null);
        return AcademicReviewSummary.from(assignment, recommendation);
    }
    private SelectionDecisionType parseDecision(String value) {
        try { return SelectionDecisionType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(
                "Recommendation must be SHORTLIST, SELECT, REJECT, or WAITLIST.", exception); }
    }
    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Academic hierarchy snapshot could not be serialized.", exception); }
    }

    private record ReviewCandidate(
            ApplicationProgrammeChoice choice,
            ProgrammeHierarchyResolution hierarchy) { }

    private static final class ProgrammeBatchAccumulator {
        private final ApplicationProgrammeChoice choice;
        private final ProgrammeHierarchyResolution hierarchy;
        private final Set<UUID> applicantIds = new java.util.LinkedHashSet<>();
        private final Set<UUID> eligibleApplicantIds = new java.util.LinkedHashSet<>();

        private ProgrammeBatchAccumulator(
                ApplicationProgrammeChoice choice,
                ProgrammeHierarchyResolution hierarchy) {
            this.choice = choice;
            this.hierarchy = hierarchy;
        }

        private void include(UUID applicationId, boolean eligibleForRelease) {
            applicantIds.add(applicationId);
            if (eligibleForRelease) eligibleApplicantIds.add(applicationId);
        }

        private ProgrammeBatch toBatch() {
            return new ProgrammeBatch(
                    choice.getProgrammeId(), choice.getProgrammeCode(), choice.getProgrammeName(),
                    hierarchy.owningAcademicUnit().id(), hierarchy.owningAcademicUnit().name(),
                    applicantIds.size(), eligibleApplicantIds.size());
        }
    }

    private static final class AcademicUnitBatchAccumulator {
        private final AcademicSetupCatalogueClient.AcademicUnitHierarchyNode unit;
        private final Set<UUID> applicantIds = new java.util.LinkedHashSet<>();
        private final Set<UUID> eligibleApplicantIds = new java.util.LinkedHashSet<>();

        private AcademicUnitBatchAccumulator(AcademicSetupCatalogueClient.AcademicUnitHierarchyNode unit) {
            this.unit = unit;
        }

        private void include(UUID applicationId, boolean eligibleForRelease) {
            applicantIds.add(applicationId);
            if (eligibleForRelease) eligibleApplicantIds.add(applicationId);
        }

        private AcademicUnitBatch toBatch() {
            return new AcademicUnitBatch(
                    unit.id(), unit.academicUnitTypeCode(), unit.code(), unit.name(),
                    applicantIds.size(), eligibleApplicantIds.size());
        }
    }
}
