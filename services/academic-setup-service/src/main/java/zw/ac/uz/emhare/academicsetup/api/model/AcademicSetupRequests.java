package zw.ac.uz.emhare.academicsetup.api.model;

import zw.ac.uz.emhare.academicsetup.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModuleType;

/** @author Tinashe K */
public final class AcademicSetupRequests {

    private static final String CALENDAR_CODE_FORMAT = "[A-Za-z0-9][A-Za-z0-9_-]*";
    private static final String CALENDAR_CODE_FORMAT_MESSAGE =
            "Use only letters, numbers, hyphens, and underscores; spaces are not allowed.";

    private AcademicSetupRequests() {
    }

    public record CreateAcademicUnitType(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) int levelOrder,
            boolean leafAllowed) {
    }

    public record UpdateAcademicUnitType(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            boolean leafAllowed,
            @Min(0) long expectedVersion) {
    }

    public record CreateAcademicUnit(
            @NotNull UUID academicUnitTypeId,
            UUID parentId,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 50) String code,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 50) String legacyFacultyCode,
            @Size(max = 50) String legacyDepartmentCode) {
    }

    public record UpdateAcademicUnit(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 50) String legacyFacultyCode,
            @Size(max = 50) String legacyDepartmentCode,
            @Min(0) long expectedVersion) {
    }

    public record CreateAcademicYear(
            @NotBlank @Size(max = 50) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record UpdateAcademicYear(
            @NotBlank @Size(max = 50) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record CreateAcademicPeriodType(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) int sortOrder) {
    }

    public record UpdateAcademicPeriodType(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) int sortOrder,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record CreateAcademicPeriod(
            @NotNull UUID academicYearId,
            @NotNull UUID academicPeriodTypeId,
            @NotBlank @Pattern(regexp = CALENDAR_CODE_FORMAT, message = CALENDAR_CODE_FORMAT_MESSAGE)
            @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record UpdateAcademicPeriod(
            @NotNull UUID academicYearId,
            @NotNull UUID academicPeriodTypeId,
            @NotBlank @Pattern(regexp = CALENDAR_CODE_FORMAT, message = CALENDAR_CODE_FORMAT_MESSAGE)
            @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record CreateIntake(
            @NotNull UUID academicYearId,
            @NotBlank @Pattern(regexp = CALENDAR_CODE_FORMAT, message = CALENDAR_CODE_FORMAT_MESSAGE)
            @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Min(1) @Max(20) int maximumProgrammeChoices,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> programmeLevelIds,
            @NotNull @Size(max = 500) List<@NotNull UUID> programmeIds) {
        public CreateIntake(
                UUID academicYearId,
                String code,
                String name,
                LocalDate startsOn,
                LocalDate endsOn,
                List<UUID> programmeLevelIds,
                List<UUID> programmeIds) {
            this(academicYearId, code, name, startsOn, endsOn, 3, programmeLevelIds, programmeIds);
        }
    }

    public record UpdateIntake(
            @NotNull UUID academicYearId,
            @NotBlank @Pattern(regexp = CALENDAR_CODE_FORMAT, message = CALENDAR_CODE_FORMAT_MESSAGE)
            @Size(max = 50) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Min(1) @Max(20) int maximumProgrammeChoices,
            @NotEmpty @Size(max = 100) List<@NotNull UUID> programmeLevelIds,
            @NotNull @Size(max = 500) List<@NotNull UUID> programmeIds,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
        public UpdateIntake(
                UUID academicYearId,
                String code,
                String name,
                LocalDate startsOn,
                LocalDate endsOn,
                List<UUID> programmeLevelIds,
                List<UUID> programmeIds,
                String changeReason,
                long expectedVersion) {
            this(
                    academicYearId, code, name, startsOn, endsOn, 3,
                    programmeLevelIds, programmeIds, changeReason, expectedVersion);
        }
    }

    public record CreateProgrammeLevel(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Min(1) int sortOrder) {
    }

    public record UpdateProgrammeLevel(
            @NotBlank @Size(max = 120) String name,
            @Min(1) int sortOrder,
            @Min(0) long expectedVersion) {
    }

    public record CreateProgrammeType(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name) {
    }

    public record UpdateProgrammeType(
            @NotBlank @Size(max = 120) String name,
            @Min(0) long expectedVersion) {
    }

    public record CreateProgramme(
            @NotNull UUID owningAcademicUnitId,
            @NotNull UUID programmeTypeId,
            @NotNull UUID programmeLevelId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") @Size(max = 5) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String awardName,
            @Min(1) @Max(100) int minimumDurationPeriods,
            @Min(1) @Max(100) int maximumDurationPeriods,
            @Size(max = 50) String legacyProgrammeCode) {
    }

    public record UpdateProgramme(
            @NotNull UUID owningAcademicUnitId,
            @NotNull UUID programmeTypeId,
            @NotNull UUID programmeLevelId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") @Size(max = 5) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String awardName,
            @Min(1) @Max(100) int minimumDurationPeriods,
            @Min(1) @Max(100) int maximumDurationPeriods,
            @Size(max = 50) String legacyProgrammeCode,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record CreateProgrammeVersion(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*") @Size(max = 40) String versionCode,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record ConfigureProgrammeEntryOptions(
            @Min(0) int minimumSelections,
            @Min(0) int maximumSelections,
            @NotNull @Size(max = 100) List<@NotNull ProgrammeEntryOptionInput> options,
            @Min(0) long expectedVersion) {
    }

    public record ProgrammeEntryOptionInput(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Min(1) int sortOrder) {
    }

    public record CreateAcademicModule(
            @NotNull UUID owningAcademicUnitId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal creditValue,
            @Min(1) @Max(100) int academicLevel,
            @Size(max = 50) String legacyCourseCode) {
    }

    public record UpdateAcademicModule(
            @NotNull UUID owningAcademicUnitId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal creditValue,
            @Min(1) @Max(100) int academicLevel,
            @Size(max = 50) String legacyCourseCode,
            @Min(0) long expectedVersion) {
    }

    public record AddCurriculumModule(
            @NotNull UUID moduleId,
            @Min(1) @Max(100) int periodNumber,
            @NotNull CurriculumModuleType moduleType,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal creditValue,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumMarkRequired,
            @Min(1) int sortOrder,
            @Size(min = 10, max = 1000) String changeReason) {
    }

    public record UpdateCurriculumModule(
            @Min(1) @Max(100) int periodNumber,
            @NotNull CurriculumModuleType moduleType,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal creditValue,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumMarkRequired,
            @Min(1) int sortOrder,
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record RemoveCurriculumModule(
            @NotBlank @Size(min = 10, max = 1000) String changeReason,
            @Min(0) long expectedVersion) {
    }

    public record VersionedAction(@Min(0) long expectedVersion) {
    }

    public record RetireProgrammeVersion(
            @Min(0) long expectedVersion,
            @NotNull LocalDate retirementDate) {
    }
}
