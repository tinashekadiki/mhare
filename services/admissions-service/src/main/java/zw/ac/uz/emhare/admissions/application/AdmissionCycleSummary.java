package zw.ac.uz.emhare.admissions.application;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public record AdmissionCycleSummary(
        UUID id,
        UUID academicYearId,
        UUID intakeId,
        String code,
        String name,
        Instant opensAt,
        Instant closesAt,
        String status,
        int maximumProgrammeChoices,
        UUID applicationTypeId,
        String applicationTypeName,
        String changeReason,
        long version) {

    static AdmissionCycleSummary from(AdmissionCycle cycle) {
        return new AdmissionCycleSummary(
                cycle.getId(), cycle.getAcademicYearId(), cycle.getIntakeId(), cycle.getCode(), cycle.getName(),
                cycle.getOpensAt(), cycle.getClosesAt(), cycle.getStatus().name(), cycle.getMaximumProgrammeChoices(),
                cycle.getApplicationType() == null ? null : cycle.getApplicationType().getId(),
                cycle.getApplicationType() == null ? null : cycle.getApplicationType().getName(),
                cycle.getChangeReason(), cycle.getVersion());
    }
}
