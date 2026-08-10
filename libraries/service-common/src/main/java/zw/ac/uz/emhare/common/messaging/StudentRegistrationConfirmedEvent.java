package zw.ac.uz.emhare.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative roster snapshot emitted after academic and registry approval.
 *
 * @author Tinashe K
 */
public record StudentRegistrationConfirmedEvent(
        UUID eventId,
        int schemaVersion,
        Instant occurredAt,
        UUID registrationSessionId,
        UUID studentId,
        String studentNumber,
        UUID programmeEnrolmentId,
        UUID programmeId,
        UUID programmeVersionId,
        UUID owningAcademicUnitId,
        String owningAcademicUnitCode,
        String owningAcademicUnitName,
        UUID programmeLevelId,
        String programmeLevelCode,
        String programmeLevelName,
        UUID academicPeriodId,
        String academicPeriodCode,
        String academicPeriodName,
        LocalDate academicPeriodStartsOn,
        LocalDate academicPeriodEndsOn,
        int programmePeriodNumber,
        List<RegisteredModule> modules) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public record RegisteredModule(
            UUID registrationModuleId,
            UUID curriculumModuleId,
            UUID moduleId,
            String moduleCode,
            String moduleName,
            String curriculumModuleType,
            BigDecimal creditValue,
            BigDecimal minimumMarkRequired) {
    }
}
