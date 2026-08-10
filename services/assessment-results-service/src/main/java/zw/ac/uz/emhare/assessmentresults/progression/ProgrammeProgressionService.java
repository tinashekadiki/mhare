package zw.ac.uz.emhare.assessmentresults.progression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.assessmentresults.integration.AssessmentResultsIntegrationOutboxService;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.CalculateDecision;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.CreateRuleSet;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.Outcome;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionCommands.WorkflowDecision;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.DecisionResultSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.DecisionSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.OutcomeSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.RosterSummary;
import zw.ac.uz.emhare.assessmentresults.progression.ProgressionViews.RuleSetSummary;
import zw.ac.uz.emhare.assessmentresults.result.ModuleResult;
import zw.ac.uz.emhare.assessmentresults.result.PublishedResult;
import zw.ac.uz.emhare.assessmentresults.roster.RegistrationRosterImport;

/** @author Tinashe K */
@Service
public class ProgrammeProgressionService {

    private static final String INITIAL_CALCULATION_REASON =
            "Calculated from complete current published Module results.";

    private final ProgressionRuleSetRepository ruleSetRepository;
    private final ProgressionRuleOutcomeRepository ruleOutcomeRepository;
    private final ProgressionRosterImportRepository rosterImportRepository;
    private final ProgressionRosterEntryRepository rosterEntryRepository;
    private final ProgressionPublishedResultRepository publishedResultRepository;
    private final StudentOverallDecisionRepository decisionRepository;
    private final StudentOverallDecisionResultRepository decisionResultRepository;
    private final StudentOverallDecisionEventRepository decisionEventRepository;
    private final AssessmentResultsIntegrationOutboxService integrationOutboxService;
    private final Clock clock;

    public ProgrammeProgressionService(
            ProgressionRuleSetRepository ruleSetRepository,
            ProgressionRuleOutcomeRepository ruleOutcomeRepository,
            ProgressionRosterImportRepository rosterImportRepository,
            ProgressionRosterEntryRepository rosterEntryRepository,
            ProgressionPublishedResultRepository publishedResultRepository,
            StudentOverallDecisionRepository decisionRepository,
            StudentOverallDecisionResultRepository decisionResultRepository,
            StudentOverallDecisionEventRepository decisionEventRepository,
            AssessmentResultsIntegrationOutboxService integrationOutboxService,
            Clock clock) {
        this.ruleSetRepository = ruleSetRepository;
        this.ruleOutcomeRepository = ruleOutcomeRepository;
        this.rosterImportRepository = rosterImportRepository;
        this.rosterEntryRepository = rosterEntryRepository;
        this.publishedResultRepository = publishedResultRepository;
        this.decisionRepository = decisionRepository;
        this.decisionResultRepository = decisionResultRepository;
        this.decisionEventRepository = decisionEventRepository;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public RuleSetSummary createRuleSet(CreateRuleSet command) {
        validateOutcomes(command.outcomes());
        int nextVersion = ruleSetRepository.findAllByDeletedAtIsNullOrderByRuleCodeAscRuleVersionDesc().stream()
                .filter(rule -> rule.getRuleCode().equalsIgnoreCase(command.ruleCode().trim()))
                .mapToInt(ProgressionRuleSet::getRuleVersion)
                .max()
                .orElse(0) + 1;
        ProgressionRuleSet ruleSet = ruleSetRepository.saveAndFlush(new ProgressionRuleSet(
                command.ruleCode(),
                command.ruleName(),
                command.programmeId(),
                command.programmeVersionId(),
                command.programmePeriodNumber(),
                nextVersion));
        List<ProgressionRuleOutcome> outcomes = command.outcomes().stream()
                .map(outcome -> toEntity(ruleSet, outcome))
                .toList();
        ruleOutcomeRepository.saveAllAndFlush(outcomes);
        return ruleSetView(ruleSet, outcomes);
    }

    @Transactional
    public RuleSetSummary approveRuleSet(UUID ruleSetId, WorkflowDecision command, UUID actorUserId) {
        ProgressionRuleSet ruleSet = requireRuleSet(ruleSetId);
        List<ProgressionRuleOutcome> outcomes = outcomes(ruleSetId);
        validatePersistedOutcomes(outcomes);
        ruleSetRepository.findByProgrammeVersionIdAndProgrammePeriodNumberAndStatusAndDeletedAtIsNull(
                        ruleSet.getProgrammeVersionId(),
                        ruleSet.getProgrammePeriodNumber(),
                        ProgressionRuleSet.Status.APPROVED)
                .filter(existing -> !existing.getId().equals(ruleSetId))
                .ifPresent(existing -> {
                    existing.supersede();
                    ruleSetRepository.saveAndFlush(existing);
                });
        ruleSet.approve(actorUserId, command.reason(), clock.instant(), command.expectedVersion());
        return ruleSetView(ruleSetRepository.saveAndFlush(ruleSet), outcomes);
    }

    @Transactional
    public DecisionSummary calculate(CalculateDecision command, UUID actorUserId) {
        RegistrationRosterImport rosterImport = rosterImportRepository
                .findByIdAndDeletedAtIsNull(command.registrationRosterImportId())
                .orElseThrow(() -> new IllegalArgumentException("Registration roster import was not found."));
        ProgressionRuleSet ruleSet = requireRuleSet(command.progressionRuleSetId());
        validateRuleScope(ruleSet, rosterImport);
        List<ProgressionRuleOutcome> outcomes = outcomes(ruleSet.getId());
        List<PublishedResult> currentResults = publishedResultRepository
                .findCurrentByRosterImportId(rosterImport.getId());
        int eligibleModules = rosterEntryRepository
                .findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(rosterImport.getId(), "ELIGIBLE")
                .size();
        if (eligibleModules == 0 || currentResults.size() != eligibleModules) {
            throw new IllegalStateException(
                    "Progression requires one current published result for every eligible registered Module.");
        }

        ProgressionMetrics metrics = calculateMetrics(currentResults);
        ProgressionRuleOutcome matchedOutcome = outcomes.stream()
                .filter(outcome -> outcome.matches(metrics))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Approved progression rules do not produce an outcome."));

        StudentOverallDecision previousDecision = decisionRepository
                .findFirstByRosterImportIdAndDeletedAtIsNullOrderByDecisionVersionDesc(rosterImport.getId())
                .orElse(null);
        validateRecalculation(previousDecision, ruleSet, currentResults);
        int decisionVersion = previousDecision == null ? 1 : previousDecision.getDecisionVersion() + 1;
        UUID supersedesDecisionId = previousDecision == null ? null : previousDecision.getId();
        Instant calculatedAt = clock.instant();
        String decisionNumber = "PRG-" + rosterImport.getAcademicPeriodCode() + "-"
                + rosterImport.getStudentNumber() + "-V" + decisionVersion + "-" + calculatedAt.toEpochMilli();
        StudentOverallDecision decision = decisionRepository.saveAndFlush(new StudentOverallDecision(
                ruleSet,
                rosterImport,
                matchedOutcome,
                metrics,
                decisionNumber,
                decisionVersion,
                supersedesDecisionId,
                actorUserId,
                calculatedAt));
        List<StudentOverallDecisionResult> evidence = currentResults.stream()
                .map(result -> new StudentOverallDecisionResult(decision, result))
                .toList();
        decisionResultRepository.saveAllAndFlush(evidence);
        decisionEventRepository.save(new StudentOverallDecisionEvent(
                decision, null, INITIAL_CALCULATION_REASON, actorUserId, calculatedAt));
        return decisionView(decision, evidence);
    }

    @Transactional
    public DecisionSummary moveDecision(
            UUID decisionId,
            String action,
            WorkflowDecision command,
            UUID actorUserId) {
        StudentOverallDecision decision = requireDecision(decisionId);
        Instant occurredAt = clock.instant();
        StudentOverallDecision.Status previousStatus = switch (action) {
            case "review" -> decision.review(actorUserId, command.reason(), occurredAt, command.expectedVersion());
            case "approve" -> decision.approve(actorUserId, command.reason(), occurredAt, command.expectedVersion());
            case "publish" -> decision.publish(actorUserId, command.reason(), occurredAt, command.expectedVersion());
            case "reject" -> decision.reject(actorUserId, command.reason(), occurredAt, command.expectedVersion());
            default -> throw new IllegalArgumentException("Unsupported progression action.");
        };
        StudentOverallDecision savedDecision = decisionRepository.saveAndFlush(decision);
        decisionEventRepository.save(new StudentOverallDecisionEvent(
                savedDecision, previousStatus, command.reason(), actorUserId, occurredAt));
        List<StudentOverallDecisionResult> evidence = decisionResults(savedDecision.getId());
        if (savedDecision.getStatus() == StudentOverallDecision.Status.PUBLISHED) {
            integrationOutboxService.enqueueProgressionDecision(savedDecision, evidence);
        }
        return decisionView(savedDecision, evidence);
    }

    @Transactional(readOnly = true)
    public List<RuleSetSummary> ruleSets() {
        return ruleSetRepository.findAllByDeletedAtIsNullOrderByRuleCodeAscRuleVersionDesc().stream()
                .map(ruleSet -> ruleSetView(ruleSet, outcomes(ruleSet.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RosterSummary> rosters() {
        return rosterImportRepository.findAllByDeletedAtIsNullOrderByImportedAtDesc().stream()
                .map(roster -> {
                    int eligibleModules = rosterEntryRepository
                            .findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(roster.getId(), "ELIGIBLE")
                            .size();
                    int publishedModules = publishedResultRepository.findCurrentByRosterImportId(roster.getId()).size();
                    return new RosterSummary(
                            roster.getId(),
                            roster.getStudentId(),
                            roster.getStudentNumber(),
                            roster.getProgrammeId(),
                            roster.getProgrammeVersionId(),
                            roster.getAcademicPeriodCode(),
                            roster.getProgrammePeriodNumber(),
                            eligibleModules,
                            publishedModules,
                            eligibleModules > 0 && eligibleModules == publishedModules);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DecisionSummary> decisions() {
        return decisionRepository.findAllByDeletedAtIsNullOrderByCalculatedAtDesc().stream()
                .map(decision -> decisionView(decision, decisionResults(decision.getId())))
                .toList();
    }

    static ProgressionMetrics calculateMetrics(List<PublishedResult> results) {
        BigDecimal attemptedCredits = BigDecimal.ZERO;
        BigDecimal passedCredits = BigDecimal.ZERO;
        BigDecimal failedCredits = BigDecimal.ZERO;
        BigDecimal weightedMarks = BigDecimal.ZERO;
        int failedModules = 0;
        int failedCompulsoryModules = 0;
        for (PublishedResult result : results) {
            var rosterEntry = result.getModuleResult().getRosterEntry();
            BigDecimal credits = rosterEntry.getCreditValue();
            attemptedCredits = attemptedCredits.add(credits);
            weightedMarks = weightedMarks.add(result.getFinalMark().multiply(credits));
            boolean passing = result.getModuleResult().getResultStatus() == ModuleResult.Status.PASS;
            if (passing) {
                passedCredits = passedCredits.add(credits);
            } else {
                failedCredits = failedCredits.add(credits);
                failedModules++;
                if ("COMPULSORY".equals(rosterEntry.getCurriculumModuleType())) {
                    failedCompulsoryModules++;
                }
            }
        }
        if (attemptedCredits.signum() <= 0) {
            throw new IllegalStateException("Progression cannot be calculated without positive registered credits.");
        }
        BigDecimal weightedAverage = weightedMarks.divide(attemptedCredits, 2, RoundingMode.HALF_UP);
        return new ProgressionMetrics(
                attemptedCredits,
                passedCredits,
                failedCredits,
                failedModules,
                failedCompulsoryModules,
                weightedAverage);
    }

    private void validateRuleScope(ProgressionRuleSet ruleSet, RegistrationRosterImport rosterImport) {
        if (ruleSet.getStatus() != ProgressionRuleSet.Status.APPROVED) {
            throw new IllegalStateException("An approved progression rule set is required.");
        }
        if (!ruleSet.getProgrammeId().equals(rosterImport.getProgrammeId())
                || !ruleSet.getProgrammeVersionId().equals(rosterImport.getProgrammeVersionId())
                || ruleSet.getProgrammePeriodNumber() != rosterImport.getProgrammePeriodNumber()) {
            throw new IllegalStateException(
                    "The progression rule does not match the student's programme version and period.");
        }
    }

    private void validateRecalculation(
            StudentOverallDecision previousDecision,
            ProgressionRuleSet ruleSet,
            List<PublishedResult> currentResults) {
        if (previousDecision == null) {
            return;
        }
        if (previousDecision.getStatus() != StudentOverallDecision.Status.PUBLISHED
                && previousDecision.getStatus() != StudentOverallDecision.Status.REJECTED) {
            throw new IllegalStateException("Complete or reject the active progression decision before recalculating.");
        }
        Set<UUID> previousEvidenceIds = decisionResults(previousDecision.getId()).stream()
                .map(item -> item.getPublishedResult().getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> currentEvidenceIds = currentResults.stream()
                .map(PublishedResult::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (previousDecision.getStatus() == StudentOverallDecision.Status.PUBLISHED
                && previousDecision.getRuleSet().getId().equals(ruleSet.getId())
                && previousEvidenceIds.equals(currentEvidenceIds)) {
            throw new IllegalStateException(
                    "The current published progression decision already uses this rule and result evidence.");
        }
    }

    private void validateOutcomes(List<Outcome> outcomes) {
        if (outcomes.size() < 2) {
            throw new IllegalArgumentException("Progression rules require at least one threshold and a fallback outcome.");
        }
        Set<Integer> priorities = new HashSet<>();
        int fallbackCount = 0;
        int maximumPriority = outcomes.stream().mapToInt(Outcome::priority).max().orElseThrow();
        for (Outcome outcome : outcomes) {
            if (!priorities.add(outcome.priority())) {
                throw new IllegalArgumentException("Progression outcome priorities must be unique.");
            }
            if (outcome.fallbackOutcome()) {
                fallbackCount++;
                if (outcome.priority() != maximumPriority || hasThreshold(outcome)) {
                    throw new IllegalArgumentException("The fallback must be final and cannot contain thresholds.");
                }
            } else if (!hasThreshold(outcome)) {
                throw new IllegalArgumentException("Every non-fallback progression outcome requires a threshold.");
            }
        }
        if (fallbackCount != 1) {
            throw new IllegalArgumentException("Exactly one final progression fallback outcome is required.");
        }
    }

    private void validatePersistedOutcomes(List<ProgressionRuleOutcome> outcomes) {
        List<Outcome> commands = outcomes.stream()
                .map(outcome -> new Outcome(
                        outcome.getPriority(),
                        outcome.getDecisionCode(),
                        outcome.getDecisionLabel(),
                        outcome.getMinimumWeightedAverage(),
                        outcome.getMinimumPassedCredits(),
                        outcome.getMaximumFailedCredits(),
                        outcome.getMaximumFailedModules(),
                        outcome.isRequireAllCompulsoryPassed(),
                        outcome.getNextProgrammePeriodNumber(),
                        outcome.isFallbackOutcome()))
                .toList();
        validateOutcomes(commands);
    }

    private boolean hasThreshold(Outcome outcome) {
        return outcome.minimumWeightedAverage() != null
                || outcome.minimumPassedCredits() != null
                || outcome.maximumFailedCredits() != null
                || outcome.maximumFailedModules() != null
                || outcome.requireAllCompulsoryPassed();
    }

    private ProgressionRuleOutcome toEntity(ProgressionRuleSet ruleSet, Outcome outcome) {
        return new ProgressionRuleOutcome(
                ruleSet,
                outcome.priority(),
                outcome.decisionCode(),
                outcome.decisionLabel(),
                outcome.minimumWeightedAverage(),
                outcome.minimumPassedCredits(),
                outcome.maximumFailedCredits(),
                outcome.maximumFailedModules(),
                outcome.requireAllCompulsoryPassed(),
                outcome.nextProgrammePeriodNumber(),
                outcome.fallbackOutcome());
    }

    private ProgressionRuleSet requireRuleSet(UUID id) {
        return ruleSetRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Progression rule set was not found."));
    }

    private StudentOverallDecision requireDecision(UUID id) {
        return decisionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Progression decision was not found."));
    }

    private List<ProgressionRuleOutcome> outcomes(UUID ruleSetId) {
        return ruleOutcomeRepository.findAllByRuleSetIdAndDeletedAtIsNullOrderByPriorityAsc(ruleSetId);
    }

    private List<StudentOverallDecisionResult> decisionResults(UUID decisionId) {
        return decisionResultRepository.findAllByDecisionIdAndDeletedAtIsNullOrderByModuleCodeAsc(decisionId);
    }

    private RuleSetSummary ruleSetView(ProgressionRuleSet ruleSet, List<ProgressionRuleOutcome> outcomes) {
        return new RuleSetSummary(
                ruleSet.getId(),
                ruleSet.getRuleCode(),
                ruleSet.getRuleName(),
                ruleSet.getProgrammeId(),
                ruleSet.getProgrammeVersionId(),
                ruleSet.getProgrammePeriodNumber(),
                ruleSet.getRuleVersion(),
                ruleSet.getStatus(),
                ruleSet.getVersion(),
                ruleSet.getApprovedByUserId(),
                ruleSet.getApprovedAt(),
                outcomes.stream().map(this::outcomeView).toList());
    }

    private OutcomeSummary outcomeView(ProgressionRuleOutcome outcome) {
        return new OutcomeSummary(
                outcome.getId(),
                outcome.getPriority(),
                outcome.getDecisionCode(),
                outcome.getDecisionLabel(),
                outcome.getMinimumWeightedAverage(),
                outcome.getMinimumPassedCredits(),
                outcome.getMaximumFailedCredits(),
                outcome.getMaximumFailedModules(),
                outcome.isRequireAllCompulsoryPassed(),
                outcome.getNextProgrammePeriodNumber(),
                outcome.isFallbackOutcome());
    }

    private DecisionSummary decisionView(
            StudentOverallDecision decision,
            List<StudentOverallDecisionResult> evidence) {
        return new DecisionSummary(
                decision.getId(),
                decision.getDecisionNumber(),
                decision.getDecisionVersion(),
                decision.getSupersedesDecisionId(),
                decision.getRuleSet().getId(),
                decision.getRuleSet().getRuleCode(),
                decision.getRosterImport().getId(),
                decision.getStudentId(),
                decision.getStudentNumber(),
                decision.getProgrammeId(),
                decision.getProgrammeVersionId(),
                decision.getAcademicPeriodCode(),
                decision.getProgrammePeriodNumber(),
                decision.getDecisionCode(),
                decision.getDecisionLabel(),
                decision.getNextProgrammePeriodNumber(),
                decision.getAttemptedCredits(),
                decision.getPassedCredits(),
                decision.getFailedCredits(),
                decision.getFailedModules(),
                decision.getFailedCompulsoryModules(),
                decision.getWeightedAverage(),
                decision.getStatus(),
                decision.getStatusReason(),
                decision.getVersion(),
                decision.getCalculatedByUserId(),
                decision.getCalculatedAt(),
                decision.getReviewedByUserId(),
                decision.getReviewedAt(),
                decision.getApprovedByUserId(),
                decision.getApprovedAt(),
                decision.getPublishedByUserId(),
                decision.getPublishedAt(),
                evidence.stream().map(this::decisionResultView).toList());
    }

    private DecisionResultSummary decisionResultView(StudentOverallDecisionResult result) {
        return new DecisionResultSummary(
                result.getPublishedResult().getId(),
                result.getModuleCode(),
                result.getModuleName(),
                result.getCurriculumModuleType(),
                result.getCreditValue(),
                result.getFinalMark(),
                result.getGrade(),
                result.getRemark(),
                result.isPassing(),
                result.getPublicationVersion());
    }
}
