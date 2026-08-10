package zw.ac.uz.emhare.assessmentresults.progression;

import java.math.BigDecimal;

/** @author Tinashe K */
public record ProgressionMetrics(
        BigDecimal attemptedCredits,
        BigDecimal passedCredits,
        BigDecimal failedCredits,
        int failedModules,
        int failedCompulsoryModules,
        BigDecimal weightedAverage) {
}
