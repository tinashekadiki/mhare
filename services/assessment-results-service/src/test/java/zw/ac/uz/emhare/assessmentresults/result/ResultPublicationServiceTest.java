package zw.ac.uz.emhare.assessmentresults.result;

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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCalculationEvidenceService;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCalculationEvidenceService.CalculationEvidence;
import zw.ac.uz.emhare.assessmentresults.assessment.AssessmentCalculationEvidenceService.OutcomeEvidence;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationOutcome;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationRun;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentModuleOffering;
import zw.ac.uz.emhare.assessmentresults.integration.AssessmentResultsIntegrationOutboxService;
import zw.ac.uz.emhare.assessmentresults.result.api.model.ResultRequests.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.infrastructure.persistence.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.AssessmentRosterEntry;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/**
 * @author Tinashe K
 */
class ResultPublicationServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");
  private static final UUID SCHEME_ID = new UUID(0, 1);
  private static final UUID RUN_ID = new UUID(0, 2);
  private static final UUID BATCH_ID = new UUID(0, 3);
  private static final UUID STUDENT_ID = new UUID(0, 4);
  private static final UUID MODULE_ID = new UUID(0, 5);
  private static final UUID PERIOD_ID = new UUID(0, 6);
  private static final UUID RESULT_ID = new UUID(0, 7);
  private static final UUID PUBLICATION_ID = new UUID(0, 8);
  private static final UUID AMENDMENT_ID = new UUID(0, 9);
  private static final UUID REQUESTER = new UUID(0, 11);
  private static final UUID REVIEWER = new UUID(0, 12);
  private static final UUID APPROVER = new UUID(0, 13);
  private static final UUID PUBLISHER = new UUID(0, 14);
  private static final Decision DECISION = new Decision(0, "Evidence checked");

  private final GradingSchemeRepository schemes = mock(GradingSchemeRepository.class);
  private final GradingBandRepository bands = mock(GradingBandRepository.class);
  private final ResultBatchRepository batches = mock(ResultBatchRepository.class);
  private final ModuleResultRepository results = mock(ModuleResultRepository.class);
  private final ResultBatchStatusEventRepository batchEvents =
      mock(ResultBatchStatusEventRepository.class);
  private final PublishedResultRepository publications = mock(PublishedResultRepository.class);
  private final PublishedResultAmendmentRepository amendments =
      mock(PublishedResultAmendmentRepository.class);
  private final PublishedResultAmendmentEventRepository amendmentEvents =
      mock(PublishedResultAmendmentEventRepository.class);
  private final AssessmentCalculationEvidenceService evidence =
      mock(AssessmentCalculationEvidenceService.class);
  private final AssessmentResultsIntegrationOutboxService outbox =
      mock(AssessmentResultsIntegrationOutboxService.class);
  private final ResultPublicationService service =
      new ResultPublicationService(
          schemes,
          bands,
          batches,
          results,
          batchEvents,
          publications,
          amendments,
          amendmentEvents,
          evidence,
          outbox,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final AssessmentModuleOffering offering = mock(AssessmentModuleOffering.class);
  private final AssessmentCalculationRun run = mock(AssessmentCalculationRun.class);
  private final RegistrationRosterImport rosterImport = mock(RegistrationRosterImport.class);
  private final AssessmentRosterEntry roster = mock(AssessmentRosterEntry.class);
  private final List<ModuleResult> savedResults = new ArrayList<>();
  private GradingScheme scheme;
  private ResultBatch batch;
  private List<GradingBand> gradingBands;

  @BeforeEach
  void setUp() {
    scheme = identify(new GradingScheme("STANDARD", "Standard grading", 1), SCHEME_ID);
    gradingBands = validBands().stream().map(this::entityBand).toList();
    when(schemes.findByIdAndDeletedAtIsNull(SCHEME_ID)).thenReturn(Optional.of(scheme));
    when(schemes.saveAndFlush(any()))
        .thenAnswer(invocation -> identify(invocation.getArgument(0), SCHEME_ID));
    when(bands.findAllByGradingSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(SCHEME_ID))
        .thenAnswer(invocation -> gradingBands);
    when(run.getId()).thenReturn(RUN_ID);
    when(run.getModuleOffering()).thenReturn(offering);
    when(offering.getModuleId()).thenReturn(MODULE_ID);
    when(offering.getModuleCode()).thenReturn("CSC101");
    when(offering.getModuleName()).thenReturn("Computing");
    when(offering.getAcademicPeriodId()).thenReturn(PERIOD_ID);
    when(offering.getAcademicPeriodCode()).thenReturn("2026-S1");
    when(roster.getRosterImport()).thenReturn(rosterImport);
    when(rosterImport.getStudentId()).thenReturn(STUDENT_ID);
    when(rosterImport.getStudentNumber()).thenReturn("R260001");
    batch = identify(new ResultBatch(run, scheme, "RES-2026-S1-CSC101"), BATCH_ID);
    when(batches.findByIdAndDeletedAtIsNull(BATCH_ID)).thenAnswer(invocation -> Optional.of(batch));
    when(batches.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              batch = identify(invocation.getArgument(0), BATCH_ID);
              return batch;
            });
    when(results.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<ModuleResult> values = invocation.getArgument(0);
              values.forEach(
                  value ->
                      savedResults.add(identify(value, new UUID(0, 100 + savedResults.size()))));
              return List.copyOf(savedResults);
            });
    when(results.findAllByResultBatchIdAndDeletedAtIsNull(BATCH_ID))
        .thenAnswer(invocation -> List.copyOf(savedResults));
    when(amendments.saveAndFlush(any()))
        .thenAnswer(invocation -> identify(invocation.getArgument(0), AMENDMENT_ID));
    when(publications.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(publications.saveAndFlush(any()))
        .thenAnswer(invocation -> identify(invocation.getArgument(0), new UUID(0, 200)));
  }

  @Test
  void versionsSchemesByCaseInsensitiveCodeAndPersistsAllBands() {
    when(schemes.findAllByDeletedAtIsNullOrderByCodeAscSchemeVersionDesc())
        .thenReturn(
            List.of(
                new GradingScheme("STANDARD", "Old", 2),
                new GradingScheme("OTHER", "Unrelated", 99)));
    var created =
        service.createGrading(
            new CreateGradingScheme("standard", "Standard grading", validBands()));
    assertEquals(3, created.schemeVersion());
    assertEquals("STANDARD", created.code());
    assertEquals(GradingScheme.Status.DRAFT, created.status());
    assertEquals(2, created.bands().size());
    assertFalse(created.bands().getFirst().passing());
    assertTrue(created.bands().getLast().passing());
    verify(bands)
        .saveAll(
            argThat(
                values -> {
                  List<GradingBand> captured = new ArrayList<>();
                  values.forEach(captured::add);
                  return captured.size() == 2
                      && captured.getFirst().getGradingScheme().getSchemeVersion() == 3;
                }));
  }

  @Test
  void firstSchemeStartsAtVersionOneAndApprovalRetainsActorAndReason() {
    assertEquals(
        1,
        service
            .createGrading(new CreateGradingScheme("STANDARD", "Standard grading", validBands()))
            .schemeVersion());
    var approved = service.approveGrading(SCHEME_ID, DECISION, APPROVER);
    assertEquals(GradingScheme.Status.APPROVED, approved.status());
    assertEquals(APPROVER, scheme.getApprovedByUserId());
    assertEquals(NOW, scheme.getApprovedAt());
    assertEquals(DECISION.reason(), scheme.getApprovalReason());
    when(schemes.findAllByDeletedAtIsNullOrderByCodeAscSchemeVersionDesc())
        .thenReturn(List.of(scheme));
    assertEquals(List.of(approved), service.grading());
    assertThrows(
        IllegalStateException.class, () -> service.approveGrading(SCHEME_ID, DECISION, APPROVER));
  }

  static Stream<List<Band>> invalidBands() {
    return Stream.of(
        null,
        List.of(),
        List.of(band("1", "100", true)),
        List.of(band("0", "99", false)),
        List.of(band("0", "49", false), band("50", "100", true)),
        List.of(band("0", "50", false), band("50", "100", true)));
  }

  @ParameterizedTest
  @MethodSource("invalidBands")
  void rejectsIncompleteOverlappingOrGappedBandDefinitionsBeforeSaving(List<Band> invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createGrading(new CreateGradingScheme("INVALID", "Invalid grading", invalid)));
    verify(schemes, never()).saveAndFlush(any());
    verify(bands, never()).saveAll(any());
  }

  @ParameterizedTest
  @MethodSource("invalidBands")
  void revalidatesPersistedBandsBeforeApproval(List<Band> invalid) {
    gradingBands = invalid == null ? List.of() : invalid.stream().map(this::entityBand).toList();
    assertThrows(
        IllegalStateException.class, () -> service.approveGrading(SCHEME_ID, DECISION, APPROVER));
    assertEquals(GradingScheme.Status.DRAFT, scheme.getStatus());
    verify(schemes, never()).saveAndFlush(any());
  }

  @Test
  void refusesUnknownAndStaleGradingSchemes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.approveGrading(UUID.randomUUID(), DECISION, APPROVER));
    assertThrows(
        IllegalStateException.class,
        () -> service.approveGrading(SCHEME_ID, new Decision(1, "Stale"), APPROVER));
    verify(schemes, never()).saveAndFlush(any());
  }

  @Test
  void materialisesPassAndFailResultsAtExactBandBoundariesFromImmutableEvidence() {
    approveScheme();
    var low = outcome("49.99");
    var high = outcome("50.00");
    when(evidence.requireComplete(RUN_ID))
        .thenReturn(
            new CalculationEvidence(
                run,
                List.of(
                    new OutcomeEvidence(low, decimal("19.99"), decimal("30.00")),
                    new OutcomeEvidence(high, decimal("20.00"), decimal("30.00")))));
    var created = service.createBatch(new CreateResultBatch(RUN_ID, SCHEME_ID), REQUESTER);
    assertEquals(ResultBatch.Status.DRAFT, created.status());
    assertEquals("RES-2026-S1-CSC101-" + NOW.toEpochMilli(), created.batchNumber());
    assertEquals(2, created.resultCount());
    assertEquals(ModuleResult.Status.FAIL, created.results().getFirst().status());
    assertEquals(ModuleResult.Status.PASS, created.results().getLast().status());
    assertEquals(decimal("19.99"), created.results().getFirst().courseworkMark());
    assertEquals(decimal("30.00"), created.results().getFirst().examinationMark());
    assertEquals("R260001", created.results().getFirst().studentNumber());
    verify(batchEvents).save(any(ResultBatchStatusEvent.class));
    verifyNoInteractions(publications, outbox);
    when(batches.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(batch));
    assertEquals(List.of(created), service.batches());
  }

  @Test
  void rejectsDuplicateRunsUnapprovedGradingAndIncompleteCalculationEvidence() {
    var command = new CreateResultBatch(RUN_ID, SCHEME_ID);
    when(batches.existsByCalculationRunIdAndDeletedAtIsNull(RUN_ID)).thenReturn(true);
    assertThrows(IllegalStateException.class, () -> service.createBatch(command, REQUESTER));
    verifyNoInteractions(evidence);
    when(batches.existsByCalculationRunIdAndDeletedAtIsNull(RUN_ID)).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> service.createBatch(command, REQUESTER));
    approveScheme();
    when(evidence.requireComplete(RUN_ID))
        .thenThrow(new IllegalStateException("Calculation is incomplete"));
    assertEquals(
        "Calculation is incomplete",
        assertThrows(IllegalStateException.class, () -> service.createBatch(command, REQUESTER))
            .getMessage());
    verify(batches, never()).saveAndFlush(any());
    verify(results, never()).saveAll(any());
  }

  @Test
  void rejectsCalculatedMarksOutsideApprovedBands() {
    approveScheme();
    AssessmentCalculationOutcome uncoveredOutcome = outcome("100.01");
    when(evidence.requireComplete(RUN_ID))
        .thenReturn(
            new CalculationEvidence(
                run,
                List.of(new OutcomeEvidence(uncoveredOutcome, decimal("40"), decimal("60.01")))));
    assertEquals(
        "Approved grading bands do not cover a calculated mark.",
        assertThrows(
                IllegalStateException.class,
                () -> service.createBatch(new CreateResultBatch(RUN_ID, SCHEME_ID), REQUESTER))
            .getMessage());
    verify(results, never()).saveAll(any());
    verifyNoInteractions(batchEvents, outbox);
  }

  @Test
  void requiresIndependentApprovalsAndOnlyPublishesAfterTheFullWorkflow() {
    savedResults.add(moduleResult("72.00", gradingBands.getLast()));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveBatch(BATCH_ID, "publish", DECISION, PUBLISHER));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveBatch(BATCH_ID, "unknown", DECISION, PUBLISHER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveBatch(BATCH_ID, "submit", new Decision(1, "Stale"), REQUESTER));
    assertEquals(
        ResultBatch.Status.SUBMITTED,
        service.moveBatch(BATCH_ID, "submit", DECISION, REQUESTER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveBatch(BATCH_ID, "moderate", DECISION, REQUESTER));
    assertEquals(
        ResultBatch.Status.MODERATED,
        service.moveBatch(BATCH_ID, "moderate", DECISION, REVIEWER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveBatch(BATCH_ID, "approve", DECISION, REVIEWER));
    assertEquals(
        ResultBatch.Status.APPROVED,
        service.moveBatch(BATCH_ID, "approve", DECISION, APPROVER).status());
    verify(publications, never()).saveAllAndFlush(any());
    verifyNoInteractions(outbox);
    var published = service.moveBatch(BATCH_ID, "publish", DECISION, PUBLISHER);
    assertEquals(ResultBatch.Status.PUBLISHED, published.status());
    assertEquals(PUBLISHER, published.publishedByUserId());
    assertEquals(NOW, published.publishedAt());
    ArgumentCaptor<PublishedResult> captured = ArgumentCaptor.forClass(PublishedResult.class);
    verify(outbox).enqueuePublishedResult(captured.capture());
    assertEquals(STUDENT_ID, captured.getValue().getStudentId());
    assertEquals(MODULE_ID, captured.getValue().getModuleId());
    assertEquals(PERIOD_ID, captured.getValue().getAcademicPeriodId());
    assertEquals(decimal("72.00"), captured.getValue().getFinalMark());
    assertEquals(1, captured.getValue().getPublicationVersion());
    verify(batchEvents, times(4)).save(any());
  }

  @Test
  void blocksRepublishingAnExistingStudentModulePeriodWithoutAnAmendment() {
    approveBatch();
    savedResults.add(moduleResult("72", gradingBands.getLast()));
    existingPublication();
    assertTrue(
        assertThrows(
                IllegalStateException.class,
                () -> service.moveBatch(BATCH_ID, "publish", DECISION, PUBLISHER))
            .getMessage()
            .contains("governed amendment"));
    assertEquals(ResultBatch.Status.APPROVED, batch.getStatus());
    verify(batches, never()).saveAndFlush(any());
    verify(publications, never()).saveAllAndFlush(any());
    verifyNoInteractions(outbox);
  }

  @ParameterizedTest
  @ValueSource(strings = {"student", "module", "period", "unapproved", "unchanged"})
  void refusesIneligibleReplacementEvidence(String mismatch) {
    PublishedResult original = existingPublication();
    approveBatch();
    ModuleResult replacement =
        moduleResult(mismatch.equals("unchanged") ? "60.0" : "72.00", gradingBands.getLast());
    when(results.findByIdAndDeletedAtIsNull(RESULT_ID)).thenReturn(Optional.of(replacement));
    if (mismatch.equals("student")) when(rosterImport.getStudentId()).thenReturn(new UUID(0, 90));
    if (mismatch.equals("module")) when(offering.getModuleId()).thenReturn(new UUID(0, 90));
    if (mismatch.equals("period")) when(offering.getAcademicPeriodId()).thenReturn(new UUID(0, 90));
    if (mismatch.equals("unapproved"))
      batch = identify(new ResultBatch(run, scheme, "DRAFT"), BATCH_ID);
    if (mismatch.equals("unapproved")) {
      ModuleResult draftResult = moduleResult("72", gradingBands.getLast());
      when(results.findByIdAndDeletedAtIsNull(RESULT_ID)).thenReturn(Optional.of(draftResult));
    }
    assertThrows(
        IllegalStateException.class,
        () ->
            service.requestAmendment(
                new RequestPublishedResultAmendment(
                    original.getId(), RESULT_ID, "Correction evidence"),
                REQUESTER));
    verify(amendments, never()).saveAndFlush(any());
    verifyNoInteractions(amendmentEvents, outbox);
  }

  @Test
  void rejectsMissingOrSupersededPublicationsAtTheAmendmentBoundary() {
    PublishedResult original = existingPublication();
    when(publications
            .findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
                STUDENT_ID, MODULE_ID, PERIOD_ID))
        .thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> service.correctionSources(PUBLICATION_ID));
    PublishedResult latest =
        identify(
            new PublishedResult(batch, moduleResult("75", gradingBands.getLast()), PUBLISHER, NOW),
            new UUID(0, 99));
    when(publications
            .findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
                STUDENT_ID, MODULE_ID, PERIOD_ID))
        .thenReturn(Optional.of(latest));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.requestAmendment(
                new RequestPublishedResultAmendment(original.getId(), RESULT_ID, "Correction"),
                REQUESTER));
    verifyNoInteractions(amendments, amendmentEvents, outbox);
  }

  @Test
  void filtersCorrectionSourcesByMarkGradeOrRemarkIgnoringDecimalScale() {
    existingPublication();
    approveBatch();
    ModuleResult same = moduleResult("60.0", gradingBands.getLast());
    ModuleResult mark = moduleResult("70.00", gradingBands.getLast());
    ModuleResult grade =
        moduleResult(
            "60.00", new GradingBand(scheme, decimal("0"), decimal("100"), "A", "Pass", true, 1));
    ModuleResult remark =
        moduleResult(
            "60.00",
            new GradingBand(scheme, decimal("0"), decimal("100"), "P", "Distinction", true, 1));
    when(results.findCorrectionSources(
            STUDENT_ID, MODULE_ID, PERIOD_ID, ResultBatch.Status.APPROVED))
        .thenReturn(List.of(same, mark, grade, remark));
    var sources = service.correctionSources(PUBLICATION_ID);
    assertEquals(3, sources.size());
    assertEquals(decimal("70.00"), sources.getFirst().finalMark());
    assertEquals("A", sources.get(1).grade());
    assertEquals("Distinction", sources.getLast().remark());
    assertEquals(NOW, sources.getFirst().approvedAt());
  }

  @Test
  void appliesGovernedAmendmentsAsNewImmutablePublicationsAndEmitsTheReplacement() {
    PublishedResult original = existingPublication();
    PublishedResultAmendment amendment = requestCorrection();
    assertEquals(PublishedResultAmendment.Status.REQUESTED, amendment.getStatus());
    verifyNoInteractions(outbox);
    assertThrows(
        IllegalStateException.class,
        () -> service.moveAmendment(AMENDMENT_ID, "review", DECISION, REQUESTER));
    assertEquals(
        PublishedResultAmendment.Status.REVIEWED,
        service.moveAmendment(AMENDMENT_ID, "review", DECISION, REVIEWER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveAmendment(AMENDMENT_ID, "approve", DECISION, REVIEWER));
    assertEquals(
        PublishedResultAmendment.Status.APPROVED,
        service.moveAmendment(AMENDMENT_ID, "approve", DECISION, APPROVER).status());
    assertThrows(
        IllegalStateException.class,
        () -> service.moveAmendment(AMENDMENT_ID, "apply", DECISION, APPROVER));
    var applied = service.moveAmendment(AMENDMENT_ID, "apply", DECISION, PUBLISHER);
    assertEquals(PublishedResultAmendment.Status.APPLIED, applied.status());
    assertEquals(PUBLISHER, applied.appliedByUserId());
    assertEquals(NOW, applied.appliedAt());
    ArgumentCaptor<PublishedResult> captured = ArgumentCaptor.forClass(PublishedResult.class);
    verify(outbox).enqueuePublishedResult(captured.capture());
    PublishedResult replacement = captured.getValue();
    assertEquals(2, replacement.getPublicationVersion());
    assertEquals(PUBLICATION_ID, replacement.getSupersedesPublishedResultId());
    assertEquals(AMENDMENT_ID, replacement.getResultAmendmentId());
    assertEquals(decimal("72.00"), replacement.getFinalMark());
    assertEquals(decimal("60.00"), original.getFinalMark());
    assertEquals(1, original.getPublicationVersion());
    verify(amendmentEvents, times(4)).save(any());
    when(amendments.findAllByDeletedAtIsNullOrderByRequestedAtDesc())
        .thenReturn(List.of(amendment));
    assertEquals(List.of(applied), service.amendments());
  }

  @Test
  void rejectsAnAmendmentWithoutPublishingAndValidatesActionsAndVersions() {
    existingPublication();
    requestCorrection();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveAmendment(AMENDMENT_ID, "unknown", DECISION, REVIEWER));
    assertThrows(
        IllegalStateException.class,
        () -> service.moveAmendment(AMENDMENT_ID, "review", new Decision(1, "Stale"), REVIEWER));
    var rejected = service.moveAmendment(AMENDMENT_ID, "reject", DECISION, REVIEWER);
    assertEquals(PublishedResultAmendment.Status.REJECTED, rejected.status());
    assertEquals(REVIEWER, rejected.rejectedByUserId());
    assertEquals(DECISION.reason(), rejected.rejectionReason());
    verify(publications, never()).saveAndFlush(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void refusesUnknownBatchPublicationResultAndAmendmentIds() {
    UUID unknown = new UUID(0, 999);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveBatch(unknown, "submit", DECISION, REQUESTER));
    assertThrows(IllegalArgumentException.class, () -> service.correctionSources(unknown));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.moveAmendment(unknown, "review", DECISION, REVIEWER));
    existingPublication();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.requestAmendment(
                new RequestPublishedResultAmendment(PUBLICATION_ID, unknown, "Correction"),
                REQUESTER));
  }

  @ParameterizedTest
  @CsvSource({"-1,0,0,1", "2,200,2,100", "1,25,1,25"})
  void boundsPublicationPagingAndTrimsStudentSearch(
      int page, int size, int safePage, int safeSize) {
    PublishedResult original = existingPublication();
    PageRequest pageable = PageRequest.of(safePage, safeSize);
    when(publications.findCurrentPublishedResults("R260001", pageable))
        .thenReturn(new PageImpl<>(List.of(original), pageable, 300));
    var response = service.publishedResults(" R260001 ", page, size);
    assertEquals(safePage, response.page());
    assertEquals(safeSize, response.size());
    assertEquals(300, response.totalElements());
    assertEquals("R260001", response.content().getFirst().studentNumber());
    assertEquals(PUBLICATION_ID, response.content().getFirst().id());
    assertEquals(decimal("60.00"), response.content().getFirst().finalMark());
    assertEquals(1, response.content().getFirst().publicationVersion());
  }

  @Test
  void supportsUnfilteredEmptyPublicationPages() {
    when(publications.findCurrentPublishedResults("", PageRequest.of(0, 25)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
    var response = service.publishedResults(null, 0, 25);
    assertTrue(response.content().isEmpty());
    assertEquals(0, response.totalElements());
  }

  private PublishedResultAmendment requestCorrection() {
    approveBatch();
    ModuleResult replacement = moduleResult("72.00", gradingBands.getLast());
    when(results.findByIdAndDeletedAtIsNull(RESULT_ID)).thenReturn(Optional.of(replacement));
    var requested =
        service.requestAmendment(
            new RequestPublishedResultAmendment(
                PUBLICATION_ID, RESULT_ID, "Corrected assessment evidence"),
            REQUESTER);
    ArgumentCaptor<PublishedResultAmendment> captured =
        ArgumentCaptor.forClass(PublishedResultAmendment.class);
    verify(amendments).saveAndFlush(captured.capture());
    when(amendments.findByIdAndDeletedAtIsNull(AMENDMENT_ID))
        .thenReturn(Optional.of(captured.getValue()));
    assertEquals(decimal("72.00"), requested.proposedFinalMark());
    assertEquals(REQUESTER, requested.requestedByUserId());
    return captured.getValue();
  }

  private PublishedResult existingPublication() {
    ResultBatch originalBatch =
        identify(new ResultBatch(run, scheme, "RES-ORIGINAL"), new UUID(0, 20));
    originalBatch.submit(REQUESTER, "Submitted", NOW.minusSeconds(300), 0);
    originalBatch.moderate(REVIEWER, "Moderated", NOW.minusSeconds(240), 0);
    originalBatch.approve(APPROVER, "Approved", NOW.minusSeconds(180), 0);
    originalBatch.publish(PUBLISHER, "Published", NOW.minusSeconds(60), 0);
    ModuleResult originalResult =
        identify(
            new ModuleResult(
                originalBatch,
                outcome("60.00"),
                decimal("20.00"),
                decimal("40.00"),
                gradingBands.getLast()),
            new UUID(0, 21));
    PublishedResult publication =
        identify(
            new PublishedResult(originalBatch, originalResult, PUBLISHER, NOW.minusSeconds(60)),
            PUBLICATION_ID);
    when(publications.findByIdAndDeletedAtIsNull(PUBLICATION_ID))
        .thenReturn(Optional.of(publication));
    when(publications
            .findFirstByStudentIdAndModuleIdAndAcademicPeriodIdAndDeletedAtIsNullOrderByPublicationVersionDesc(
                STUDENT_ID, MODULE_ID, PERIOD_ID))
        .thenReturn(Optional.of(publication));
    return publication;
  }

  private void approveScheme() {
    scheme.approve(APPROVER, "Approved grading policy", NOW, 0);
  }

  private void approveBatch() {
    batch.submit(REQUESTER, "Submitted evidence", NOW, 0);
    batch.moderate(REVIEWER, "Moderated evidence", NOW, 0);
    batch.approve(APPROVER, "Approved evidence", NOW, 0);
  }

  private AssessmentCalculationOutcome outcome(String total) {
    AssessmentCalculationOutcome outcome = mock(AssessmentCalculationOutcome.class);
    when(outcome.getRosterEntry()).thenReturn(roster);
    when(outcome.getWeightedTotal()).thenReturn(decimal(total));
    return outcome;
  }

  private ModuleResult moduleResult(String total, GradingBand band) {
    return identify(
        new ModuleResult(
            batch,
            outcome(total),
            decimal("20.00"),
            decimal(total).subtract(decimal("20.00")),
            band),
        RESULT_ID);
  }

  private GradingBand entityBand(Band band) {
    return new GradingBand(
        scheme,
        band.minimumMark(),
        band.maximumMark(),
        band.grade(),
        band.remark(),
        band.passing(),
        band.sortOrder());
  }

  private static List<Band> validBands() {
    return List.of(band("0", "49.99", false), band("50.00", "100", true));
  }

  private static Band band(String minimum, String maximum, boolean passing) {
    return new Band(
        decimal(minimum),
        decimal(maximum),
        passing ? "P" : "F",
        passing ? "Pass" : "Fail",
        passing,
        passing ? 2 : 1);
  }

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  private static <T extends AuditableEntity> T identify(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
