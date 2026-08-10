package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Assessment-and-Results publication projection contract.
 *
 * @author Tinashe K
 */
public record PublishedResultVersionCreatedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID publishedResultId,
        UUID resultBatchId,
        UUID moduleResultId,
        UUID studentId,
        String studentNumber,
        UUID programmeEnrolmentId,
        UUID programmeId,
        UUID programmeVersionId,
        UUID academicPeriodId,
        String academicPeriodCode,
        UUID moduleId,
        String moduleCode,
        String moduleName,
        String curriculumModuleType,
        BigDecimal creditValue,
        BigDecimal finalMark,
        String grade,
        String remark,
        boolean passing,
        int publicationVersion,
        UUID supersedesPublishedResultId,
        UUID resultAmendmentId,
        UUID publishedByUserId,
        Instant publishedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
