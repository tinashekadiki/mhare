package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationResult;
import zw.ac.uz.emhare.admissions.domain.model.ApplicantQualificationSitting;
import zw.ac.uz.emhare.admissions.domain.model.GradingScale;
import zw.ac.uz.emhare.admissions.domain.model.GradingScaleValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.application.QualificationPointsCalculator.EligibilitySnapshot;
import zw.ac.uz.emhare.admissions.application.QualificationPointsCalculator.ExclusionReason;
import zw.ac.uz.emhare.admissions.domain.model.QualificationLevel;

/** @author Tinashe K */
class QualificationPointsCalculatorTest {

    private static final GradingScale A_LEVEL_SCALE =
            new GradingScale("ZIMSEC-A", "ZIMSEC A Level", QualificationLevel.A_LEVEL, LocalDate.of(2020, 1, 1), null);
    private static final GradingScale O_LEVEL_SCALE =
            new GradingScale("ZIMSEC-O", "ZIMSEC O Level", QualificationLevel.O_LEVEL, LocalDate.of(2020, 1, 1), null);

    private static final Map<String, GradingScaleValue> A_LEVEL_VALUES = Map.of(
            "A", new GradingScaleValue(A_LEVEL_SCALE, "A", new BigDecimal("5.00"), true, 1),
            "B", new GradingScaleValue(A_LEVEL_SCALE, "B", new BigDecimal("4.00"), true, 2),
            "C", new GradingScaleValue(A_LEVEL_SCALE, "C", new BigDecimal("3.00"), true, 3),
            "D", new GradingScaleValue(A_LEVEL_SCALE, "D", new BigDecimal("2.00"), true, 4),
            "E", new GradingScaleValue(A_LEVEL_SCALE, "E", new BigDecimal("1.00"), true, 5),
            "F", new GradingScaleValue(A_LEVEL_SCALE, "F", BigDecimal.ZERO, false, 6));

    private static final Map<String, GradingScaleValue> O_LEVEL_VALUES = Map.of(
            "C", new GradingScaleValue(O_LEVEL_SCALE, "C", null, true, 3),
            "U", new GradingScaleValue(O_LEVEL_SCALE, "U", null, false, 8));

    private static final QualificationPointsCalculator.GradingScaleResolver STUB_RESOLVER = (sitting, grade) -> {
        Map<String, GradingScaleValue> values = sitting.getLevel() == QualificationLevel.A_LEVEL ? A_LEVEL_VALUES : O_LEVEL_VALUES;
        return Optional.ofNullable(values.get(grade));
    };

    @Test
    void sumsPointsFromPassingALevelResultsOnly() {
        ApplicantQualificationSitting aLevelSitting = sitting(QualificationLevel.A_LEVEL);
        List<ApplicantQualificationResult> results = List.of(
                result(aLevelSitting, "A"),
                result(aLevelSitting, "E"));

        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, STUB_RESOLVER);

        assertEquals(new BigDecimal("6.00"), snapshot.totalPoints());
        assertEquals(2, snapshot.consideredResults().size());
        assertTrue(snapshot.excludedResults().isEmpty());
    }

    @Test
    void appliesCompleteZimsecALevelGradeMapping() {
        ApplicantQualificationSitting aLevelSitting = sitting(QualificationLevel.A_LEVEL);
        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(List.of(
                result(aLevelSitting, "A"), result(aLevelSitting, "B"), result(aLevelSitting, "C"),
                result(aLevelSitting, "D"), result(aLevelSitting, "E")), STUB_RESOLVER);

        assertEquals(new BigDecimal("15.00"), snapshot.totalPoints());
    }

    @Test
    void excludesFailingResultsFromPointsAndConsideration() {
        ApplicantQualificationSitting aLevelSitting = sitting(QualificationLevel.A_LEVEL);
        List<ApplicantQualificationResult> results = List.of(
                result(aLevelSitting, "A"),
                result(aLevelSitting, "F"));

        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, STUB_RESOLVER);

        assertEquals(new BigDecimal("5.00"), snapshot.totalPoints());
        assertEquals(1, snapshot.consideredResults().size());
        assertEquals(1, snapshot.excludedResults().size());
        assertEquals(ExclusionReason.FAILED, snapshot.excludedResults().get(0).reason());
    }

    @Test
    void excludesUngradedResultsWithNoMatchingGradingScaleValue() {
        ApplicantQualificationSitting aLevelSitting = sitting(QualificationLevel.A_LEVEL);
        List<ApplicantQualificationResult> results = List.of(result(aLevelSitting, "X"));

        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, STUB_RESOLVER);

        assertEquals(BigDecimal.ZERO, snapshot.totalPoints());
        assertTrue(snapshot.consideredResults().isEmpty());
        assertEquals(ExclusionReason.UNGRADED, snapshot.excludedResults().get(0).reason());
    }

    @Test
    void countsPassingOLevelResultsAsConsideredButNotTowardsPoints() {
        ApplicantQualificationSitting oLevelSitting = sitting(QualificationLevel.O_LEVEL);
        List<ApplicantQualificationResult> results = List.of(result(oLevelSitting, "C"));

        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, STUB_RESOLVER);

        assertEquals(BigDecimal.ZERO, snapshot.totalPoints());
        assertEquals(1, snapshot.consideredResults().size());
        assertTrue(snapshot.excludedResults().isEmpty());
    }

    @Test
    void excludesFailingOLevelResults() {
        ApplicantQualificationSitting oLevelSitting = sitting(QualificationLevel.O_LEVEL);
        List<ApplicantQualificationResult> results = List.of(result(oLevelSitting, "U"));

        EligibilitySnapshot snapshot = QualificationPointsCalculator.compute(results, STUB_RESOLVER);

        assertTrue(snapshot.consideredResults().isEmpty());
        assertEquals(ExclusionReason.FAILED, snapshot.excludedResults().get(0).reason());
    }

    private static ApplicantQualificationSitting sitting(QualificationLevel level) {
        return new ApplicantQualificationSitting(null, level, null, "CENTRE", "CAND", 2025);
    }

    private static ApplicantQualificationResult result(ApplicantQualificationSitting sitting, String grade) {
        return new ApplicantQualificationResult(sitting, null, "Subject", grade);
    }
}
