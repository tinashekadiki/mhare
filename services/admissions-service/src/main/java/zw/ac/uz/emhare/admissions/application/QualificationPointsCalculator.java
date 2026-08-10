package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements FR-SEL-015 and the A-Level-only points clause of FR-SEL-003: only passing subject
 * results under the applicable grading scale are considered, and only A Level results contribute
 * to total points.
 *
 * @author Tinashe K
 */
public final class QualificationPointsCalculator {

    private QualificationPointsCalculator() {
    }

    public static EligibilitySnapshot compute(List<ApplicantQualificationResult> results, GradingScaleResolver resolver) {
        List<ConsideredResult> considered = new ArrayList<>();
        List<ExcludedResult> excluded = new ArrayList<>();
        BigDecimal totalPoints = BigDecimal.ZERO;

        for (ApplicantQualificationResult result : results) {
            ApplicantQualificationSitting sitting = result.getQualificationSitting();
            Optional<GradingScaleValue> gradingScaleValue = resolver.resolve(sitting, result.getGrade());
            if (gradingScaleValue.isEmpty()) {
                excluded.add(new ExcludedResult(result, ExclusionReason.UNGRADED));
                continue;
            }
            GradingScaleValue value = gradingScaleValue.get();
            if (!value.isPass()) {
                excluded.add(new ExcludedResult(result, ExclusionReason.FAILED));
                continue;
            }
            considered.add(new ConsideredResult(result, value));
            if (sitting.getLevel() == QualificationLevel.A_LEVEL && value.getPoints() != null) {
                totalPoints = totalPoints.add(value.getPoints());
            }
        }
        return new EligibilitySnapshot(totalPoints, considered, excluded);
    }

    @FunctionalInterface
    public interface GradingScaleResolver {
        Optional<GradingScaleValue> resolve(ApplicantQualificationSitting sitting, String grade);
    }

    public enum ExclusionReason {
        FAILED,
        UNGRADED
    }

    public record ConsideredResult(ApplicantQualificationResult result, GradingScaleValue gradingScaleValue) {
    }

    public record ExcludedResult(ApplicantQualificationResult result, ExclusionReason reason) {
    }

    public record EligibilitySnapshot(
            BigDecimal totalPoints, List<ConsideredResult> consideredResults, List<ExcludedResult> excludedResults) {
    }
}
