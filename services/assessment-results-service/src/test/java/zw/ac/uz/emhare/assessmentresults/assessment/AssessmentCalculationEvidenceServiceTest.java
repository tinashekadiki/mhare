package zw.ac.uz.emhare.assessmentresults.assessment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationComponentEvidence;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationOutcome;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentCalculationRun;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums.CalculationStatus;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums.ComponentType;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationComponentEvidenceRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationOutcomeRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence.AssessmentCalculationRunRepository;

/**
 * @author Tinashe K
 */
class AssessmentCalculationEvidenceServiceTest {
  private static final UUID RUN_ID = new UUID(0, 1);
  private static final UUID OUTCOME_ID = new UUID(0, 2);
  private final AssessmentCalculationRunRepository runs =
      mock(AssessmentCalculationRunRepository.class);
  private final AssessmentCalculationOutcomeRepository outcomes =
      mock(AssessmentCalculationOutcomeRepository.class);
  private final AssessmentCalculationComponentEvidenceRepository components =
      mock(AssessmentCalculationComponentEvidenceRepository.class);
  private final AssessmentCalculationEvidenceService service =
      new AssessmentCalculationEvidenceService(runs, outcomes, components);

  @Test
  void rejectsAnUnknownCalculationRunWithoutReadingOutcomes() {
    assertEquals(
        "Assessment calculation run was not found.",
        assertThrows(IllegalArgumentException.class, () -> service.requireComplete(RUN_ID))
            .getMessage());
    verifyNoInteractions(outcomes, components);
  }

  @ParameterizedTest
  @CsvSource({"RUNNING,0", "FAILED,0", "COMPLETED,1"})
  void refusesUnfinishedOrPartiallyCalculatedRuns(CalculationStatus status, int incompleteCount) {
    AssessmentCalculationRun run = completedRun();
    when(run.getStatus()).thenReturn(status);
    when(run.getIncompleteResultCount()).thenReturn(incompleteCount);
    assertThrows(IllegalStateException.class, () -> service.requireComplete(RUN_ID));
    verifyNoInteractions(outcomes, components);
  }

  @Test
  void requiresImmutableComponentEvidenceForEveryStudentOutcome() {
    completedRun();
    studentOutcome();
    assertEquals(
        "Calculation run has no immutable component evidence and cannot be published.",
        assertThrows(IllegalStateException.class, () -> service.requireComplete(RUN_ID))
            .getMessage());
  }

  @Test
  void separatesFinalExamsFromAllCourseworkTypesAndRoundsOnlyAfterSumming() {
    AssessmentCalculationRun run = completedRun();
    AssessmentCalculationOutcome outcome = studentOutcome();
    List<AssessmentCalculationComponentEvidence> evidence =
        List.of(
            component(ComponentType.COURSEWORK, "10.004"),
            component(ComponentType.PRACTICAL, "5.004"),
            component(ComponentType.IN_CLASS_TEST, "7.004"),
            component(ComponentType.OTHER, "3.004"),
            component(ComponentType.FINAL_EXAM, "20.004"),
            component(ComponentType.FINAL_EXAM, "30.004"));
    when(components.findAllByCalculationOutcomeIdAndDeletedAtIsNull(OUTCOME_ID))
        .thenReturn(evidence);
    var result = service.requireComplete(RUN_ID);
    assertSame(run, result.run());
    assertEquals(1, result.outcomes().size());
    assertSame(outcome, result.outcomes().getFirst().outcome());
    assertEquals(new BigDecimal("25.02"), result.outcomes().getFirst().courseworkContribution());
    assertEquals(new BigDecimal("50.01"), result.outcomes().getFirst().examinationContribution());
    verify(components).findAllByCalculationOutcomeIdAndDeletedAtIsNull(OUTCOME_ID);
  }

  @Test
  void representsAnAbsentExamComponentAsZeroWithoutInventingMarks() {
    completedRun();
    studentOutcome();
    var coursework = component(ComponentType.COURSEWORK, "40.00");
    when(components.findAllByCalculationOutcomeIdAndDeletedAtIsNull(OUTCOME_ID))
        .thenReturn(List.of(coursework));
    var result = service.requireComplete(RUN_ID).outcomes().getFirst();
    assertEquals(new BigDecimal("40.00"), result.courseworkContribution());
    assertEquals(new BigDecimal("0.00"), result.examinationContribution());
  }

  private AssessmentCalculationRun completedRun() {
    AssessmentCalculationRun run = mock(AssessmentCalculationRun.class);
    when(run.getStatus()).thenReturn(CalculationStatus.COMPLETED);
    when(runs.findByIdAndDeletedAtIsNull(RUN_ID)).thenReturn(Optional.of(run));
    return run;
  }

  private AssessmentCalculationOutcome studentOutcome() {
    AssessmentCalculationOutcome outcome = mock(AssessmentCalculationOutcome.class);
    when(outcome.getId()).thenReturn(OUTCOME_ID);
    when(outcomes.findAllByCalculationRunIdAndDeletedAtIsNull(RUN_ID)).thenReturn(List.of(outcome));
    return outcome;
  }

  private AssessmentCalculationComponentEvidence component(
      ComponentType type, String contribution) {
    AssessmentCalculationComponentEvidence evidence =
        mock(AssessmentCalculationComponentEvidence.class);
    when(evidence.getComponentType()).thenReturn(type);
    when(evidence.getWeightedContribution()).thenReturn(new BigDecimal(contribution));
    return evidence;
  }
}
