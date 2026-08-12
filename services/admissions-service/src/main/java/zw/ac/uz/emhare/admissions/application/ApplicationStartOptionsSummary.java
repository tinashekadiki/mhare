package zw.ac.uz.emhare.admissions.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ApplicationStartOptionsSummary(
        String applicantCategoryCode,
        List<ApplicantCategoryOption> applicantCategories,
        List<AdmissionIntakeOption> intakes,
        List<ApplicationTypeOption> applicationTypes,
        List<ApplicationRouteOption> routes) {

    public record ApplicantCategoryOption(String code, String label) {
    }

    public record AdmissionIntakeOption(
            UUID id,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            int maximumProgrammeChoices,
            List<ProgrammeOption> programmes) {
    }

    public record ProgrammeOption(
            UUID id, UUID programmeVersionId,
            String code, String name, String awardName,
            String owningAcademicUnitName, String programmeVersionCode,
            String programmeTypeCode, String programmeTypeName,
            String programmeLevelCode, String programmeLevelName,
            int minimumEntryOptionSelections, int maximumEntryOptionSelections,
            List<EntryOption> entryOptions) {
        public ProgrammeOption(
                UUID id, UUID programmeVersionId, String code, String name, String awardName,
                String owningAcademicUnitName, String programmeVersionCode) {
            this(id, programmeVersionId, code, name, awardName, owningAcademicUnitName, programmeVersionCode,
                    null, null, null, null, 0, 0, List.of());
        }
    }

    public record EntryOption(UUID id, String code, String name, String description, int sortOrder) { }

    public record ApplicationRouteOption(
            UUID applicationTypeId,
            String applicationTypeCode,
            String applicationTypeName,
            UUID intakeId,
            String intakeCode,
            String intakeName,
            int maximumProgrammeChoices,
            List<ProgrammeOption> programmes) { }

    public record ApplicationTypeOption(
            UUID id,
            String code,
            String name,
            boolean requiresEmploymentHistory,
            boolean requiresReferees,
            ApplicationFeeOption fee,
            List<ApplicationSectionOption> sections) {
    }

    public record ApplicationSectionOption(
            String code,
            String name,
            boolean required,
            boolean repeatable,
            int minimumRecords,
            int sortOrder) {
    }

    public record ApplicationFeeOption(
            boolean required,
            BigDecimal amount,
            String currencyCode) {
    }
}
