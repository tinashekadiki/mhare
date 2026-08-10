package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable official progression decision contract for downstream records and documents.
 *
 * @author Tinashe K
 */
public record ProgressionDecisionPublishedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID progressionDecisionId,
        String decisionNumber,
        int decisionVersion,
        UUID supersedesDecisionId,
        UUID progressionRuleSetId,
        String progressionRuleCode,
        int progressionRuleVersion,
        UUID registrationRosterImportId,
        UUID studentId,
        String studentNumber,
        UUID programmeEnrolmentId,
        UUID programmeId,
        UUID programmeVersionId,
        UUID academicPeriodId,
        String academicPeriodCode,
        int programmePeriodNumber,
        String decisionCode,
        String decisionLabel,
        Integer nextProgrammePeriodNumber,
        BigDecimal attemptedCredits,
        BigDecimal passedCredits,
        BigDecimal failedCredits,
        int failedModules,
        int failedCompulsoryModules,
        BigDecimal weightedAverage,
        UUID publishedByUserId,
        Instant publishedAt,
        List<UUID> sourcePublishedResultIds) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
