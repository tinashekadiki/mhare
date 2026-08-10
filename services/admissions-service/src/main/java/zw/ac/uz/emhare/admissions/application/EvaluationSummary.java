package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record EvaluationSummary(
        UUID id,
        UUID applicationId,
        UUID programmeChoiceId,
        UUID requirementSetId,
        String requirementSetVersion,
        String status,
        BigDecimal totalPoints,
        BigDecimal rankScore,
        Instant evaluatedAt) {

    static EvaluationSummary from(ApplicationEvaluation evaluation, UUID applicationId) {
        return new EvaluationSummary(
                evaluation.getId(), applicationId, evaluation.getProgrammeChoice().getId(),
                evaluation.getRequirementSet().getId(), evaluation.getRequirementSet().getVersionCode(),
                evaluation.getStatus().name(), evaluation.getTotalPoints(), evaluation.getRankScore(),
                evaluation.getEvaluatedAt());
    }
}
