package zw.ac.uz.emhare.academicsetup.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.academicsetup.domain.AcademicOfferingStatus;
import zw.ac.uz.emhare.academicsetup.domain.CalendarStatus;
import zw.ac.uz.emhare.academicsetup.domain.CurriculumModuleType;
import zw.ac.uz.emhare.academicsetup.domain.ProgrammeVersionStatus;
import zw.ac.uz.emhare.academicsetup.domain.ReferenceStatus;

/** @author Tinashe K */
public final class AcademicSetupViews {

    private AcademicSetupViews() {
    }

    public record AcademicUnitTypeSummary(
            UUID id, String code, String name, int levelOrder, boolean leafAllowed,
            ReferenceStatus status, long version) {
    }

    public record AcademicUnitSummary(
            UUID id, UUID academicUnitTypeId, String academicUnitTypeCode,
            UUID parentId, String code, String name, ReferenceStatus status,
            String legacyFacultyCode, String legacyDepartmentCode, long version) {
    }

    public record AcademicYearSummary(
            UUID id, String name, LocalDate startDate, LocalDate endDate,
            CalendarStatus status, String changeReason, long version) {
    }

    public record AcademicPeriodTypeSummary(
            UUID id, String code, String name, int sortOrder, ReferenceStatus status,
            String changeReason, long version) {
    }

    public record AcademicPeriodSummary(
            UUID id, UUID academicYearId, String academicYearName,
            UUID academicPeriodTypeId, String academicPeriodTypeName,
            String code, String name, LocalDate startDate, LocalDate endDate,
            CalendarStatus status, String changeReason, long version) {
    }

    public record IntakeSummary(
            UUID id, UUID academicYearId, String academicYearName,
            String code, String name, LocalDate startsOn, LocalDate endsOn,
            CalendarStatus status, int maximumProgrammeChoices, String changeReason,
            List<IntakeProgrammeLevelSummary> programmeLevels,
            List<IntakeProgrammeSummary> specificProgrammes,
            boolean allProgrammesInSelectedLevels,
            long version) {
    }

    public record IntakeProgrammeLevelSummary(UUID id, String code, String name) {
    }

    public record IntakeProgrammeSummary(
            UUID id, String code, String name,
            UUID programmeLevelId, String programmeLevelName) {
    }

    public record AdmissionsCatalogue(
            UUID academicYearId, String academicYearName,
            UUID intakeId, String intakeCode, String intakeName,
            LocalDate intakeStartsOn, LocalDate intakeEndsOn,
            List<AdmissionsProgrammeOption> programmes) {
    }

    public record AdmissionsIntakeOption(
            UUID intakeId,
            UUID academicYearId,
            String academicYearName,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            CalendarStatus status,
            int maximumProgrammeChoices,
            List<AdmissionsProgrammeOption> programmes) {
    }

    public record AdmissionsProgrammeOption(
            UUID programmeId, String programmeCode, String programmeName, String awardName,
            UUID programmeVersionId, String programmeVersionCode,
            UUID owningAcademicUnitId, String owningAcademicUnitName,
            int minimumDurationPeriods, int maximumDurationPeriods) {
    }

    public record RegistrationCatalogue(
            UUID academicPeriodId, String academicPeriodCode, String academicPeriodName,
            LocalDate academicPeriodStartsOn, LocalDate academicPeriodEndsOn,
            UUID programmeVersionId, UUID programmeId, String programmeCode,
            String programmeName, String programmeVersionCode,
            UUID owningAcademicUnitId, String owningAcademicUnitCode, String owningAcademicUnitName,
            UUID programmeLevelId, String programmeLevelCode, String programmeLevelName,
            int periodNumber,
            List<RegistrationModuleOption> modules) {
    }

    public record RegistrationModuleOption(
            UUID curriculumModuleId, UUID moduleId, String moduleCode, String moduleName,
            CurriculumModuleType moduleType, BigDecimal creditValue,
            BigDecimal minimumMarkRequired, int sortOrder) {
    }

    public record ProgrammeLevelSummary(
            UUID id, String code, String name, int sortOrder, ReferenceStatus status, long version) {
    }

    public record ProgrammeTypeSummary(
            UUID id, String code, String name, ReferenceStatus status, long version) {
    }

    public record ProgrammeSummary(
            UUID id, String code, String name, String awardName,
            UUID owningAcademicUnitId, String owningAcademicUnitName,
            UUID programmeTypeId, String programmeTypeName,
            UUID programmeLevelId, String programmeLevelName,
            int minimumDurationPeriods, int maximumDurationPeriods,
            AcademicOfferingStatus status, String legacyProgrammeCode,
            String changeReason, long version) {
    }

    public record ProgrammeHierarchyResolution(
            UUID programmeId,
            String programmeCode,
            String programmeName,
            AcademicUnitSummary owningAcademicUnit,
            AcademicUnitSummary highestAcademicUnit,
            List<AcademicUnitSummary> ancestorPath) {
    }

    public record ProgrammeVersionSummary(
            UUID id, UUID programmeId, String programmeCode, String versionCode,
            LocalDate effectiveFrom, LocalDate effectiveTo, ProgrammeVersionStatus status,
            UUID approvedByUserId, Instant approvedAt, long version,
            long curriculumModuleCount, BigDecimal totalCredits) {
    }

    public record AcademicModuleSummary(
            UUID id, String code, String name, String description,
            UUID owningAcademicUnitId, String owningAcademicUnitName,
            BigDecimal creditValue, int academicLevel,
            AcademicOfferingStatus status, String legacyCourseCode, long version) {
    }

    public record CurriculumModuleSummary(
            UUID id, UUID programmeVersionId, UUID moduleId, String moduleCode, String moduleName,
            int periodNumber, CurriculumModuleType moduleType, BigDecimal creditValue,
            BigDecimal minimumMarkRequired, int sortOrder, long version) {
    }

    public record CurriculumModuleUsageSummary(
            UUID curriculumModuleId,
            long registrationCount,
            long resultCount,
            boolean removable) {
    }

    public record AcademicSetupOverview(
            List<AcademicUnitTypeSummary> academicUnitTypes,
            List<AcademicUnitSummary> academicUnits,
            List<AcademicYearSummary> academicYears,
            List<AcademicPeriodTypeSummary> academicPeriodTypes,
            List<AcademicPeriodSummary> academicPeriods,
            List<IntakeSummary> intakes,
            List<ProgrammeLevelSummary> programmeLevels,
            List<ProgrammeTypeSummary> programmeTypes,
            List<ProgrammeSummary> programmes,
            List<AcademicModuleSummary> modules) {
    }
}
