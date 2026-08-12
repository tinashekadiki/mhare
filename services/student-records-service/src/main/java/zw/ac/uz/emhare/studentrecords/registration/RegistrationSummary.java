package zw.ac.uz.emhare.studentrecords.registration;

import zw.ac.uz.emhare.studentrecords.registration.domain.model.ModuleSelectionSource;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationStatus;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.RegistrationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record RegistrationSummary(
        UUID id,
        String registrationNumber,
        UUID studentId,
        String studentNumber,
        String studentName,
        UUID programmeEnrolmentId,
        String programmeCode,
        String programmeName,
        UUID academicPeriodId,
        String academicPeriodCode,
        String academicPeriodName,
        LocalDate academicPeriodStartsOn,
        LocalDate academicPeriodEndsOn,
        int programmePeriodNumber,
        RegistrationType registrationType,
        RegistrationStatus status,
        String statusReason,
        Instant initiatedAt,
        Instant submittedAt,
        Instant academicApprovedAt,
        Instant confirmedAt,
        long version,
        BigDecimal totalCredits,
        List<RegisteredModuleSummary> modules) {

    public record RegisteredModuleSummary(
            UUID id,
            UUID curriculumModuleId,
            UUID moduleId,
            String moduleCode,
            String moduleName,
            String curriculumModuleType,
            BigDecimal creditValue,
            BigDecimal minimumMarkRequired,
            ModuleSelectionSource selectionSource) {
    }
}
