package zw.ac.uz.emhare.assessmentresults.progression.api.model;

import zw.ac.uz.emhare.assessmentresults.progression.domain.model.ProgressionRuleOutcome;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.ProgressionRuleSet;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.StudentOverallDecision;

import zw.ac.uz.emhare.assessmentresults.progression.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public final class ProgressionResponses {
    private ProgressionResponses() {
    }

    public record RuleSetSummary(
            UUID id,
            String ruleCode,
            String ruleName,
            UUID programmeId,
            UUID programmeVersionId,
            int programmePeriodNumber,
            int ruleVersion,
            ProgressionRuleSet.Status status,
            long version,
            UUID approvedByUserId,
            Instant approvedAt,
            List<OutcomeSummary> outcomes) {
    }

    public record OutcomeSummary(
            UUID id,
            int priority,
            ProgressionRuleOutcome.DecisionCode decisionCode,
            String decisionLabel,
            BigDecimal minimumWeightedAverage,
            BigDecimal minimumPassedCredits,
            BigDecimal maximumFailedCredits,
            Integer maximumFailedModules,
            boolean requireAllCompulsoryPassed,
            Integer nextProgrammePeriodNumber,
            boolean fallbackOutcome) {
    }

    public record RosterSummary(
            UUID id,
            UUID studentId,
            String studentNumber,
            UUID programmeId,
            UUID programmeVersionId,
            String academicPeriodCode,
            int programmePeriodNumber,
            int eligibleModules,
            int publishedModules,
            boolean readyForProgression) {
    }

    public record DecisionSummary(
            UUID id,
            String decisionNumber,
            int decisionVersion,
            UUID supersedesDecisionId,
            UUID progressionRuleSetId,
            String progressionRuleCode,
            UUID registrationRosterImportId,
            UUID studentId,
            String studentNumber,
            UUID programmeId,
            UUID programmeVersionId,
            String academicPeriodCode,
            int programmePeriodNumber,
            ProgressionRuleOutcome.DecisionCode decisionCode,
            String decisionLabel,
            Integer nextProgrammePeriodNumber,
            BigDecimal attemptedCredits,
            BigDecimal passedCredits,
            BigDecimal failedCredits,
            int failedModules,
            int failedCompulsoryModules,
            BigDecimal weightedAverage,
            StudentOverallDecision.Status status,
            String statusReason,
            long version,
            UUID calculatedByUserId,
            Instant calculatedAt,
            UUID reviewedByUserId,
            Instant reviewedAt,
            UUID approvedByUserId,
            Instant approvedAt,
            UUID publishedByUserId,
            Instant publishedAt,
            List<DecisionResultSummary> results) {
    }

    public record DecisionResultSummary(
            UUID publishedResultId,
            String moduleCode,
            String moduleName,
            String curriculumModuleType,
            BigDecimal creditValue,
            BigDecimal finalMark,
            String grade,
            String remark,
            boolean passing,
            int publicationVersion) {
    }
}
