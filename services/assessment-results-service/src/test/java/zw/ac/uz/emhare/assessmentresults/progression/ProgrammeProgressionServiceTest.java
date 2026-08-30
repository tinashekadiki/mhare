package zw.ac.uz.emhare.assessmentresults.progression;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.assessmentresults.integration.AssessmentResultsIntegrationOutboxService;
import zw.ac.uz.emhare.assessmentresults.progression.api.model.ProgressionRequests.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.infrastructure.persistence.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.ModuleResult;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.PublishedResult;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
class ProgrammeProgressionServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T09:00:00Z");
  private static final UUID RULE = new UUID(0, 1),
      ROSTER = new UUID(0, 2),
      PROGRAMME = new UUID(0, 3),
      VERSION = new UUID(0, 4),
      CALCULATOR = new UUID(0, 5),
      REVIEWER = new UUID(0, 6),
      APPROVER = new UUID(0, 7),
      PUBLISHER = new UUID(0, 8);
  private static final WorkflowDecision DECISION = new WorkflowDecision(0, " Verified evidence ");
  private final ProgressionRuleSetRepository rules = mock(ProgressionRuleSetRepository.class);
  private final ProgressionRuleOutcomeRepository outcomes =
      mock(ProgressionRuleOutcomeRepository.class);
  private final ProgressionRosterImportRepository imports =
      mock(ProgressionRosterImportRepository.class);
  private final ProgressionRosterEntryRepository rosterEntries =
      mock(ProgressionRosterEntryRepository.class);
  private final ProgressionPublishedResultRepository publications =
      mock(ProgressionPublishedResultRepository.class);
  private final StudentOverallDecisionRepository decisions =
      mock(StudentOverallDecisionRepository.class);
  private final StudentOverallDecisionResultRepository evidence =
      mock(StudentOverallDecisionResultRepository.class);
  private final StudentOverallDecisionEventRepository events =
      mock(StudentOverallDecisionEventRepository.class);
  private final AssessmentResultsIntegrationOutboxService outbox =
      mock(AssessmentResultsIntegrationOutboxService.class);
  private final ProgrammeProgressionService service =
      new ProgrammeProgressionService(
          rules,
          outcomes,
          imports,
          rosterEntries,
          publications,
          decisions,
          evidence,
          events,
          outbox,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final RegistrationRosterImport roster = mock(RegistrationRosterImport.class);
  private final List<StudentOverallDecision> savedDecisions = new ArrayList<>();
  private final List<StudentOverallDecisionResult> savedEvidence = new ArrayList<>();
  private ProgressionRuleSet rule;
  private List<ProgressionRuleOutcome> ruleOutcomes;
  private List<PublishedResult> results;

  @BeforeEach
  void setUp() {
    rule =
        identify(
            new ProgressionRuleSet("STANDARD", "Standard progression", PROGRAMME, VERSION, 1, 1),
            RULE);
    ruleOutcomes = validOutcomes().stream().map(this::entityOutcome).toList();
    when(rules.findByIdAndDeletedAtIsNull(RULE)).thenAnswer(inv -> Optional.of(rule));
    when(rules.saveAndFlush(any())).thenAnswer(inv -> identify(inv.getArgument(0), RULE));
    when(outcomes.findAllByRuleSetIdAndDeletedAtIsNullOrderByPriorityAsc(RULE))
        .thenAnswer(inv -> ruleOutcomes);
    when(imports.findByIdAndDeletedAtIsNull(ROSTER)).thenReturn(Optional.of(roster));
    when(roster.getId()).thenReturn(ROSTER);
    when(roster.getProgrammeId()).thenReturn(PROGRAMME);
    when(roster.getProgrammeVersionId()).thenReturn(VERSION);
    when(roster.getProgrammePeriodNumber()).thenReturn(1);
    when(roster.getStudentId()).thenReturn(new UUID(0, 10));
    when(roster.getStudentNumber()).thenReturn("R260001");
    when(roster.getAcademicPeriodCode()).thenReturn("2026-S1");
    results =
        List.of(
            result(11, "CSC101", "COMPULSORY", "10", "70", true),
            result(12, "MAT101", "ELECTIVE", "5", "40", false));
    when(publications.findCurrentByRosterImportId(ROSTER)).thenAnswer(inv -> results);
    when(rosterEntries.findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            ROSTER, "ELIGIBLE"))
        .thenAnswer(
            inv -> results.stream().map(r -> r.getModuleResult().getRosterEntry()).toList());
    when(decisions.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              StudentOverallDecision decision = inv.getArgument(0);
              if (decision.getId() == null) {
                identify(decision, new UUID(0, 100 + savedDecisions.size()));
                savedDecisions.add(decision);
              }
              return decision;
            });
    when(decisions.findByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            inv ->
                savedDecisions.stream()
                    .filter(d -> d.getId().equals(inv.getArgument(0)))
                    .findFirst());
    when(evidence.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<StudentOverallDecisionResult> added = inv.getArgument(0);
              savedEvidence.addAll(added);
              return added;
            });
    when(evidence.findAllByDecisionIdAndDeletedAtIsNullOrderByModuleCodeAsc(any()))
        .thenAnswer(
            inv ->
                savedEvidence.stream()
                    .filter(e -> e.getDecision().getId().equals(inv.getArgument(0)))
                    .toList());
  }

  @Test
  void versionsRuleCodesIndependentlyAndPersistsGovernedOutcomes() {
    when(rules.findAllByDeletedAtIsNullOrderByRuleCodeAscRuleVersionDesc())
        .thenReturn(
            List.of(
                new ProgressionRuleSet("standard", "Older", PROGRAMME, VERSION, 1, 3),
                new ProgressionRuleSet("UNRELATED", "Other", PROGRAMME, VERSION, 1, 99)));
    var created = service.createRuleSet(create(validOutcomes()));
    assertEquals(4, created.ruleVersion());
    assertEquals("STANDARD", created.ruleCode());
    assertEquals(ProgressionRuleSet.Status.DRAFT, created.status());
    assertEquals(2, created.outcomes().size());
    assertTrue(created.outcomes().getLast().fallbackOutcome());
    verify(outcomes)
        .saveAllAndFlush(
            argThat(
                values ->
                    java.util.stream.StreamSupport.stream(values.spliterator(), false).count()
                        == 2));
  }

  @Test
  void firstVersionApprovalRetainsActorAndSupersedesOnlyOtherApprovedRule() {
    assertEquals(1, service.createRuleSet(create(validOutcomes())).ruleVersion());
    ProgressionRuleSet previous =
        identify(
            new ProgressionRuleSet("STANDARD", "Old", PROGRAMME, VERSION, 1, 1), new UUID(0, 20));
    previous.approve(APPROVER, "Previous", NOW, 0);
    when(rules.findByProgrammeVersionIdAndProgrammePeriodNumberAndStatusAndDeletedAtIsNull(
            VERSION, 1, ProgressionRuleSet.Status.APPROVED))
        .thenReturn(Optional.of(previous));
    var approved = service.approveRuleSet(RULE, DECISION, APPROVER);
    assertEquals(ProgressionRuleSet.Status.APPROVED, approved.status());
    assertEquals(APPROVER, approved.approvedByUserId());
    assertEquals(NOW, approved.approvedAt());
    assertEquals("Verified evidence", rule.getApprovalReason());
    assertEquals(ProgressionRuleSet.Status.SUPERSEDED, previous.getStatus());
  }

  @Test
  void approvalDoesNotSupersedeItselfAndRejectsStaleVersion() {
    when(rules.findByProgrammeVersionIdAndProgrammePeriodNumberAndStatusAndDeletedAtIsNull(
            VERSION, 1, ProgressionRuleSet.Status.APPROVED))
        .thenReturn(Optional.of(rule));
    assertThrows(
        IllegalStateException.class,
        () -> service.approveRuleSet(RULE, new WorkflowDecision(9, "Stale"), APPROVER));
    assertEquals(ProgressionRuleSet.Status.DRAFT, rule.getStatus());
    service.approveRuleSet(RULE, DECISION, APPROVER);
    assertThrows(
        IllegalStateException.class, () -> service.approveRuleSet(RULE, DECISION, APPROVER));
  }

  @ParameterizedTest
  @MethodSource("invalidOutcomes")
  void rejectsAmbiguousMissingOrMisorderedFallbackBeforePersistence(List<Outcome> invalid) {
    assertThrows(IllegalArgumentException.class, () -> service.createRuleSet(create(invalid)));
    verify(rules, never()).saveAndFlush(any());
    ruleOutcomes = invalid.stream().map(this::entityOutcome).toList();
    assertThrows(
        IllegalArgumentException.class, () -> service.approveRuleSet(RULE, DECISION, APPROVER));
  }

  static Stream<List<Outcome>> invalidOutcomes() {
    Outcome threshold = validOutcomes().getFirst(), fallback = validOutcomes().getLast();
    Outcome noThreshold =
        new Outcome(
            1,
            ProgressionRuleOutcome.DecisionCode.PROCEED,
            "Proceed",
            null,
            null,
            null,
            null,
            false,
            2,
            false);
    return Stream.of(
        List.of(),
        List.of(threshold),
        List.of(threshold, threshold),
        List.of(noThreshold, fallback),
        List.of(
            threshold,
            new Outcome(
                2,
                threshold.decisionCode(),
                "Other",
                BigDecimal.ONE,
                null,
                null,
                null,
                false,
                2,
                false)),
        List.of(
            new Outcome(
                1,
                fallback.decisionCode(),
                "Early fallback",
                null,
                null,
                null,
                null,
                false,
                null,
                true),
            fallback),
        List.of(
            threshold,
            new Outcome(
                2,
                fallback.decisionCode(),
                "Invalid fallback",
                BigDecimal.ONE,
                null,
                null,
                null,
                false,
                null,
                true)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"average", "passed", "failedCredits", "failedModules", "compulsory"})
  void supportsEachIndependentThreshold(String threshold) {
    Outcome condition =
        new Outcome(
            1,
            ProgressionRuleOutcome.DecisionCode.PROCEED,
            "Proceed",
            threshold.equals("average") ? BigDecimal.TEN : null,
            threshold.equals("passed") ? BigDecimal.TEN : null,
            threshold.equals("failedCredits") ? BigDecimal.TEN : null,
            threshold.equals("failedModules") ? 1 : null,
            threshold.equals("compulsory"),
            2,
            false);
    assertEquals(
        2,
        service
            .createRuleSet(create(List.of(condition, validOutcomes().getLast())))
            .outcomes()
            .size());
  }

  @Test
  void calculatesWeightedCreditsAndSnapshotsPublishedEvidenceBeforeWorkflow() {
    var calculated = calculate();
    assertEquals(new BigDecimal("60.00"), calculated.weightedAverage());
    assertEquals(new BigDecimal("15"), calculated.attemptedCredits());
    assertEquals(new BigDecimal("10"), calculated.passedCredits());
    assertEquals(new BigDecimal("5"), calculated.failedCredits());
    assertEquals(1, calculated.failedModules());
    assertEquals(0, calculated.failedCompulsoryModules());
    assertEquals(ProgressionRuleOutcome.DecisionCode.PROCEED_WITH_CARRY, calculated.decisionCode());
    assertEquals(2, calculated.nextProgrammePeriodNumber());
    assertEquals(1, calculated.decisionVersion());
    assertNull(calculated.supersedesDecisionId());
    assertEquals(NOW, calculated.calculatedAt());
    assertEquals(2, calculated.results().size());
    assertEquals("CSC101", calculated.results().getFirst().moduleCode());
    assertTrue(calculated.results().getFirst().passing());
    assertFalse(calculated.results().getLast().passing());
    verify(events).save(any(StudentOverallDecisionEvent.class));
    verifyNoInteractions(outbox);
  }

  @Test
  void failedCompulsoryModuleSelectsFallbackWithoutInventingNextPeriod() {
    results = List.of(result(11, "CSC101", "COMPULSORY", "10", "40", false));
    var calculated = calculate();
    assertEquals(ProgressionRuleOutcome.DecisionCode.REPEAT, calculated.decisionCode());
    assertEquals(1, calculated.failedCompulsoryModules());
    assertNull(calculated.nextProgrammePeriodNumber());
  }

  @ParameterizedTest
  @ValueSource(strings = {"programme", "version", "period", "draft"})
  void rejectsUnapprovedOrDifferentProgrammeScope(String mismatch) {
    if (!mismatch.equals("draft")) approveRule();
    if (mismatch.equals("programme")) when(roster.getProgrammeId()).thenReturn(new UUID(0, 99));
    if (mismatch.equals("version"))
      when(roster.getProgrammeVersionId()).thenReturn(new UUID(0, 99));
    if (mismatch.equals("period")) when(roster.getProgrammePeriodNumber()).thenReturn(2);
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
    verify(decisions, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3})
  void rejectsIncompleteOrExcessPublishedEvidence(int eligibleCount) {
    approveRule();
    when(rosterEntries.findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            ROSTER, "ELIGIBLE"))
        .thenReturn(
            java.util.Collections.nCopies(eligibleCount, mock(AssessmentRosterEntry.class)));
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
    verify(decisions, never()).saveAndFlush(any());
  }

  @Test
  void rejectsNonPositiveCreditsAndRulesWithoutAMatchingOutcome() {
    approveRule();
    results = List.of(result(11, "CSC101", "COMPULSORY", "0", "70", true));
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
    results = List.of(result(11, "CSC101", "COMPULSORY", "10", "70", true));
    ruleOutcomes = List.of();
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
  }

  @Test
  void workflowRequiresIndependentActorsAndPublishesOnlyAtTheFinalStage() {
    UUID id = calculate().id();
    assertThrows(
        IllegalStateException.class,
        () -> service.moveDecision(id, "review", DECISION, CALCULATOR));
    assertEquals(
        StudentOverallDecision.Status.REVIEWED,
        service.moveDecision(id, "review", DECISION, REVIEWER).status());
    for (UUID actor : List.of(CALCULATOR, REVIEWER))
      assertThrows(
          IllegalStateException.class, () -> service.moveDecision(id, "approve", DECISION, actor));
    assertEquals(
        StudentOverallDecision.Status.APPROVED,
        service.moveDecision(id, "approve", DECISION, APPROVER).status());
    verifyNoInteractions(outbox);
    for (UUID actor : List.of(CALCULATOR, REVIEWER, APPROVER))
      assertThrows(
          IllegalStateException.class, () -> service.moveDecision(id, "publish", DECISION, actor));
    var published = service.moveDecision(id, "publish", DECISION, PUBLISHER);
    assertEquals(StudentOverallDecision.Status.PUBLISHED, published.status());
    assertEquals(REVIEWER, published.reviewedByUserId());
    assertEquals(APPROVER, published.approvedByUserId());
    assertEquals(PUBLISHER, published.publishedByUserId());
    assertEquals(NOW, published.publishedAt());
    assertEquals("Verified evidence", published.statusReason());
    verify(outbox).enqueueProgressionDecision(savedDecisions.getFirst(), savedEvidence);
    verify(events, times(4)).save(any());
    assertThrows(
        IllegalStateException.class, () -> service.moveDecision(id, "reject", DECISION, REVIEWER));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectionFromCalculatedOrReviewedAllowsANewDecision(boolean reviewed) {
    UUID id = calculate().id();
    if (reviewed) service.moveDecision(id, "review", DECISION, REVIEWER);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveDecision(id, "reject", DECISION, CALCULATOR));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveDecision(id, "reject", new WorkflowDecision(8, "Stale"), REVIEWER));
    assertEquals(
        StudentOverallDecision.Status.REJECTED,
        service.moveDecision(id, "reject", DECISION, REVIEWER).status());
    StudentOverallDecision rejected = savedDecisions.getFirst();
    assertEquals(REVIEWER, rejected.getRejectedByUserId());
    assertEquals(NOW, rejected.getRejectedAt());
    when(decisions.findFirstByRosterImportIdAndDeletedAtIsNullOrderByDecisionVersionDesc(ROSTER))
        .thenReturn(Optional.of(rejected));
    var recalculated = service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR);
    assertEquals(2, recalculated.decisionVersion());
    assertEquals(id, recalculated.supersedesDecisionId());
    verifyNoInteractions(outbox);
  }

  @Test
  void recalculationRequiresCompletedWorkflowAndChangedPublishedEvidenceOrRule() {
    UUID id = calculate().id();
    when(decisions.findFirstByRosterImportIdAndDeletedAtIsNullOrderByDecisionVersionDesc(ROSTER))
        .thenReturn(Optional.of(savedDecisions.getFirst()));
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
    service.moveDecision(id, "review", DECISION, REVIEWER);
    service.moveDecision(id, "approve", DECISION, APPROVER);
    service.moveDecision(id, "publish", DECISION, PUBLISHER);
    assertThrows(
        IllegalStateException.class,
        () -> service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR));
    results = List.of(result(33, "CSC101", "COMPULSORY", "10", "90", true), results.getLast());
    assertEquals(
        2, service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR).decisionVersion());
    rule =
        identify(
            new ProgressionRuleSet("STANDARD", "Updated rule", PROGRAMME, VERSION, 1, 2),
            new UUID(0, 30));
    approveRule();
    when(outcomes.findAllByRuleSetIdAndDeletedAtIsNullOrderByPriorityAsc(rule.getId()))
        .thenReturn(ruleOutcomes);
    // A different approved rule is also a legitimate reason to recalculate from the same
    // publications.
    assertEquals(
        2, service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR).decisionVersion());
  }

  @Test
  void rejectsMissingRecordsUnsupportedTransitionsAndStaleDecisions() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.calculate(new CalculateDecision(new UUID(0, 99), RULE), CALCULATOR));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.approveRuleSet(new UUID(0, 99), DECISION, APPROVER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveDecision(new UUID(0, 99), "review", DECISION, REVIEWER));
    UUID id = calculate().id();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveDecision(id, "unknown", DECISION, REVIEWER));
    assertThrows(
        IllegalStateException.class, () -> service.moveDecision(id, "approve", DECISION, APPROVER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveDecision(id, "review", new WorkflowDecision(1, "Stale"), REVIEWER));
  }

  @Test
  void queryViewsExposeSavedEvidenceAndReadinessFromEligibleCounts() {
    assertTrue(service.decisions().isEmpty());
    assertTrue(service.ruleSets().isEmpty());
    assertTrue(service.rosters().isEmpty());
    calculate();
    when(decisions.findAllByDeletedAtIsNullOrderByCalculatedAtDesc()).thenReturn(savedDecisions);
    when(rules.findAllByDeletedAtIsNullOrderByRuleCodeAscRuleVersionDesc())
        .thenReturn(List.of(rule));
    when(imports.findAllByDeletedAtIsNullOrderByImportedAtDesc()).thenReturn(List.of(roster));
    assertEquals(2, service.decisions().getFirst().results().size());
    assertEquals(RULE, service.ruleSets().getFirst().id());
    assertTrue(service.rosters().getFirst().readyForProgression());
    when(rosterEntries.findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            ROSTER, "ELIGIBLE"))
        .thenReturn(List.of());
    assertFalse(service.rosters().getFirst().readyForProgression());
    when(rosterEntries.findAllByRosterImportIdAndEligibilityStatusAndDeletedAtIsNull(
            ROSTER, "ELIGIBLE"))
        .thenReturn(List.of(mock(AssessmentRosterEntry.class)));
    assertFalse(service.rosters().getFirst().readyForProgression());
  }

  @ParameterizedTest
  @CsvSource({
    "60,10,5,1,0,true",
    "59.99,10,5,1,0,false",
    "60,9.99,5,1,0,false",
    "60,10,5.01,1,0,false",
    "60,10,5,2,0,false",
    "60,10,5,1,1,false"
  })
  void thresholdComparisonsAreInclusiveAndAllMustPass(
      String average,
      String passed,
      String failed,
      int failedModules,
      int compulsory,
      boolean matches) {
    ProgressionRuleOutcome threshold =
        new ProgressionRuleOutcome(
            rule,
            1,
            ProgressionRuleOutcome.DecisionCode.PROCEED,
            "Proceed",
            new BigDecimal("60"),
            BigDecimal.TEN,
            new BigDecimal("5"),
            1,
            true,
            2,
            false);
    assertEquals(
        matches,
        threshold.matches(
            new ProgressionMetrics(
                new BigDecimal("15"),
                new BigDecimal(passed),
                new BigDecimal(failed),
                failedModules,
                compulsory,
                new BigDecimal(average))));
  }

  private zw.ac.uz.emhare.assessmentresults.progression.api.model.ProgressionResponses
          .DecisionSummary
      calculate() {
    approveRule();
    return service.calculate(new CalculateDecision(ROSTER, RULE), CALCULATOR);
  }

  private void approveRule() {
    rule.approve(APPROVER, "Approved", NOW, 0);
  }

  private static CreateRuleSet create(List<Outcome> values) {
    return new CreateRuleSet(" STANDARD ", "Standard progression", PROGRAMME, VERSION, 1, values);
  }

  private static List<Outcome> validOutcomes() {
    return List.of(
        new Outcome(
            1,
            ProgressionRuleOutcome.DecisionCode.PROCEED_WITH_CARRY,
            "Proceed with carry",
            new BigDecimal("50"),
            null,
            null,
            null,
            true,
            2,
            false),
        new Outcome(
            2,
            ProgressionRuleOutcome.DecisionCode.REPEAT,
            "Repeat",
            null,
            null,
            null,
            null,
            false,
            null,
            true));
  }

  private ProgressionRuleOutcome entityOutcome(Outcome value) {
    return new ProgressionRuleOutcome(
        rule,
        value.priority(),
        value.decisionCode(),
        value.decisionLabel(),
        value.minimumWeightedAverage(),
        value.minimumPassedCredits(),
        value.maximumFailedCredits(),
        value.maximumFailedModules(),
        value.requireAllCompulsoryPassed(),
        value.nextProgrammePeriodNumber(),
        value.fallbackOutcome());
  }

  private static <T extends AuditableEntity> T identify(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static PublishedResult result(
      long id, String code, String type, String credits, String mark, boolean passing) {
    AssessmentRosterEntry entry = mock(AssessmentRosterEntry.class);
    when(entry.getCreditValue()).thenReturn(new BigDecimal(credits));
    when(entry.getCurriculumModuleType()).thenReturn(type);
    ModuleResult module = mock(ModuleResult.class);
    when(module.getRosterEntry()).thenReturn(entry);
    when(module.getResultStatus())
        .thenReturn(passing ? ModuleResult.Status.PASS : ModuleResult.Status.FAIL);
    PublishedResult result = mock(PublishedResult.class);
    when(result.getId()).thenReturn(new UUID(0, id));
    when(result.getModuleResult()).thenReturn(module);
    when(result.getModuleCode()).thenReturn(code);
    when(result.getModuleName()).thenReturn(code + " Module");
    when(result.getFinalMark()).thenReturn(new BigDecimal(mark));
    when(result.getGrade()).thenReturn(passing ? "P" : "F");
    when(result.getRemark()).thenReturn(passing ? "Pass" : "Fail");
    when(result.getPublicationVersion()).thenReturn(1);
    return result;
  }
}
