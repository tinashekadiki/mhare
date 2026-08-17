package zw.ac.uz.emhare.academicsetup.application;

import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicModuleSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicPeriodSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicPeriodTypeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicUnitSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicUnitTypeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicYearSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.IntakeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeLevelSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeTypeSummary;

import java.util.List;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.IntakeProgrammeLevelSummary;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.IntakeProgrammeSummary;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriod;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnit;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnitType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.model.Intake;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeLevelTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.Programme;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeLevel;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeType;

/**
 * Cohesive response mapping shared by academic commands and catalogue queries. @author Tinashe K
 */
final class AcademicSetupSummaryMapper {

  private AcademicSetupSummaryMapper() {}

  static AcademicUnitTypeSummary unitType(AcademicUnitType value) {
    return new AcademicUnitTypeSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getLevelOrder(),
        value.isLeafAllowed(),
        value.getStatus(),
        value.getVersion());
  }

  static AcademicUnitSummary unit(AcademicUnit value) {
    return new AcademicUnitSummary(
        value.getId(),
        value.getAcademicUnitType().getId(),
        value.getAcademicUnitType().getCode(),
        value.getParent() == null ? null : value.getParent().getId(),
        value.getCode(),
        value.getName(),
        value.getStatus(),
        value.getLegacyFacultyCode(),
        value.getLegacyDepartmentCode(),
        value.getVersion());
  }

  static AcademicYearSummary year(AcademicYear value) {
    return new AcademicYearSummary(
        value.getId(),
        value.getName(),
        value.getStartDate(),
        value.getEndDate(),
        value.getStatus(),
        value.getChangeReason(),
        value.getVersion());
  }

  static AcademicPeriodTypeSummary periodType(AcademicPeriodType value) {
    return new AcademicPeriodTypeSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getSortOrder(),
        value.getStatus(),
        value.getChangeReason(),
        value.getVersion());
  }

  static AcademicPeriodSummary period(AcademicPeriod value) {
    return new AcademicPeriodSummary(
        value.getId(),
        value.getAcademicYear().getId(),
        value.getAcademicYear().getName(),
        value.getAcademicPeriodType().getId(),
        value.getAcademicPeriodType().getName(),
        value.getCode(),
        value.getName(),
        value.getStartDate(),
        value.getEndDate(),
        value.getStatus(),
        value.getChangeReason(),
        value.getVersion());
  }

  static IntakeSummary intake(
      Intake value,
      List<IntakeProgrammeLevelTarget> programmeLevelTargets,
      List<IntakeProgrammeTarget> programmeTargets) {
    return new IntakeSummary(
        value.getId(),
        value.getAcademicYear().getId(),
        value.getAcademicYear().getName(),
        value.getCode(),
        value.getName(),
        value.getStartsOn(),
        value.getEndsOn(),
        value.getOfferAcceptanceDeadline(),
        value.getRegistrationDate(),
        value.getOrientationDate(),
        value.getCommencementDate(),
        value.getStatus(),
        value.getMaximumProgrammeChoices(),
        value.getChangeReason(),
        programmeLevelTargets.stream()
            .map(
                target ->
                    new IntakeProgrammeLevelSummary(
                        target.getProgrammeLevel().getId(),
                        target.getProgrammeLevel().getCode(),
                        target.getProgrammeLevel().getName()))
            .toList(),
        programmeTargets.stream()
            .map(
                target ->
                    new IntakeProgrammeSummary(
                        target.getProgramme().getId(),
                        target.getProgramme().getCode(),
                        target.getProgramme().getName(),
                        target.getProgramme().getProgrammeLevel().getId(),
                        target.getProgramme().getProgrammeLevel().getName()))
            .toList(),
        programmeTargets.isEmpty(),
        value.getVersion());
  }

  static ProgrammeLevelSummary programmeLevel(ProgrammeLevel value) {
    return new ProgrammeLevelSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getSortOrder(),
        value.getStatus(),
        value.getVersion());
  }

  static ProgrammeTypeSummary programmeType(ProgrammeType value) {
    return new ProgrammeTypeSummary(
        value.getId(), value.getCode(), value.getName(), value.getStatus(), value.getVersion());
  }

  static ProgrammeSummary programme(Programme value) {
    return new ProgrammeSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getAwardName(),
        value.getOwningAcademicUnit().getId(),
        value.getOwningAcademicUnit().getName(),
        value.getProgrammeType().getId(),
        value.getProgrammeType().getName(),
        value.getProgrammeLevel().getId(),
        value.getProgrammeLevel().getName(),
        value.getMinimumDurationPeriods(),
        value.getMaximumDurationPeriods(),
        value.getStatus(),
        value.getLegacyProgrammeCode(),
        value.getChangeReason(),
        value.getVersion());
  }

  static AcademicModuleSummary module(AcademicModule value) {
    return new AcademicModuleSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getDescription(),
        value.getOwningAcademicUnit().getId(),
        value.getOwningAcademicUnit().getName(),
        value.getCreditValue(),
        value.getAcademicLevel(),
        value.getStatus(),
        value.getLegacyCourseCode(),
        value.getVersion());
  }

  static CurriculumModuleSummary curriculumModule(CurriculumModule value) {
    return new CurriculumModuleSummary(
        value.getId(),
        value.getProgrammeVersion().getId(),
        value.getAcademicModule().getId(),
        value.getAcademicModule().getCode(),
        value.getAcademicModule().getName(),
        value.getPeriodNumber(),
        value.getModuleType(),
        value.getCreditValue(),
        value.getMinimumMarkRequired(),
        value.getSortOrder(),
        value.getVersion());
  }
}
