package zw.ac.uz.emhare.academicsetup.application;

import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.AddCurriculumModule;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.ConfigureProgrammeEntryOptions;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicModule;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicPeriod;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicPeriodType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicUnit;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicUnitType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateAcademicYear;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateIntake;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateProgramme;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateProgrammeLevel;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateProgrammeType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.CreateProgrammeVersion;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicModule;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicPeriod;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicPeriodType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicUnit;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicUnitType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateAcademicYear;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateCurriculumModule;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateIntake;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateProgramme;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateProgrammeLevel;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateProgrammeType;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicModuleSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicPeriodSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicPeriodTypeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicSetupOverview;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicUnitSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicUnitTypeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicYearSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AdmissionsCatalogue;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AdmissionsIntakeOption;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.IntakeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeHierarchyResolution;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeLevelSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeTypeSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeVersionSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.RegistrationCatalogue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicOfferingStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriod;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnit;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnitType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;
import zw.ac.uz.emhare.academicsetup.domain.model.CalendarStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.model.Intake;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeLevelTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.Programme;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeLevel;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeType;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion.EntryOptionDefinition;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersionStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.ReferenceStatus;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicModuleRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicPeriodRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicPeriodTypeRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicUnitRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicUnitTypeRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.AcademicYearRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.CurriculumModuleRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.IntakeProgrammeLevelTargetRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.IntakeProgrammeTargetRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.IntakeRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.ProgrammeLevelRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.ProgrammeRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.ProgrammeTypeRepository;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.ProgrammeVersionRepository;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/**
 * Governs the institution-owned academic hierarchy, calendar, catalogue, and governed approved
 * curriculum history.
 *
 * @author Tinashe K
 */
@Service
@Transactional
public class AcademicSetupService {

  private final AcademicUnitTypeRepository academicUnitTypeRepository;
  private final AcademicUnitRepository academicUnitRepository;
  private final AcademicYearRepository academicYearRepository;
  private final AcademicPeriodTypeRepository academicPeriodTypeRepository;
  private final AcademicPeriodRepository academicPeriodRepository;
  private final IntakeRepository intakeRepository;
  private final IntakeProgrammeLevelTargetRepository intakeProgrammeLevelTargetRepository;
  private final IntakeProgrammeTargetRepository intakeProgrammeTargetRepository;
  private final ProgrammeLevelRepository programmeLevelRepository;
  private final ProgrammeTypeRepository programmeTypeRepository;
  private final ProgrammeRepository programmeRepository;
  private final ProgrammeVersionRepository programmeVersionRepository;
  private final AcademicModuleRepository academicModuleRepository;
  private final CurriculumModuleRepository curriculumModuleRepository;
  private final EmhareCurrentUserResolver currentUserResolver;
  private final Clock clock;
  private final AcademicCatalogueQueryService academicCatalogueQueryService;

  public AcademicSetupService(
      AcademicUnitTypeRepository academicUnitTypeRepository,
      AcademicUnitRepository academicUnitRepository,
      AcademicYearRepository academicYearRepository,
      AcademicPeriodTypeRepository academicPeriodTypeRepository,
      AcademicPeriodRepository academicPeriodRepository,
      IntakeRepository intakeRepository,
      IntakeProgrammeLevelTargetRepository intakeProgrammeLevelTargetRepository,
      IntakeProgrammeTargetRepository intakeProgrammeTargetRepository,
      ProgrammeLevelRepository programmeLevelRepository,
      ProgrammeTypeRepository programmeTypeRepository,
      ProgrammeRepository programmeRepository,
      ProgrammeVersionRepository programmeVersionRepository,
      AcademicModuleRepository academicModuleRepository,
      CurriculumModuleRepository curriculumModuleRepository,
      EmhareCurrentUserResolver currentUserResolver,
      Clock clock,
      AcademicCatalogueQueryService academicCatalogueQueryService) {
    this.academicUnitTypeRepository = academicUnitTypeRepository;
    this.academicUnitRepository = academicUnitRepository;
    this.academicYearRepository = academicYearRepository;
    this.academicPeriodTypeRepository = academicPeriodTypeRepository;
    this.academicPeriodRepository = academicPeriodRepository;
    this.intakeRepository = intakeRepository;
    this.intakeProgrammeLevelTargetRepository = intakeProgrammeLevelTargetRepository;
    this.intakeProgrammeTargetRepository = intakeProgrammeTargetRepository;
    this.programmeLevelRepository = programmeLevelRepository;
    this.programmeTypeRepository = programmeTypeRepository;
    this.programmeRepository = programmeRepository;
    this.programmeVersionRepository = programmeVersionRepository;
    this.academicModuleRepository = academicModuleRepository;
    this.curriculumModuleRepository = curriculumModuleRepository;
    this.currentUserResolver = currentUserResolver;
    this.clock = clock;
    this.academicCatalogueQueryService = academicCatalogueQueryService;
  }

  public AcademicUnitTypeSummary createAcademicUnitType(CreateAcademicUnitType command) {
    requireUnique(
        !academicUnitTypeRepository.existsByCodeIgnoreCase(command.code()),
        "Academic unit type code already exists.");
    requireUnique(
        !academicUnitTypeRepository.existsByLevelOrder(command.levelOrder()),
        "Academic unit type level already exists.");
    if (command.levelOrder() > 1
        && !academicUnitTypeRepository.existsByLevelOrder(command.levelOrder() - 1)) {
      throw new IllegalArgumentException(
          "Academic unit type levels must be configured in sequence.");
    }
    return AcademicSetupSummaryMapper.unitType(
        academicUnitTypeRepository.saveAndFlush(
            new AcademicUnitType(
                command.code(), command.name(), command.levelOrder(), command.leafAllowed())));
  }

  public AcademicUnitTypeSummary updateAcademicUnitType(
      UUID academicUnitTypeId, UpdateAcademicUnitType command) {
    AcademicUnitType unitType = requireAcademicUnitType(academicUnitTypeId);
    requireUnique(
        !academicUnitTypeRepository.existsByCodeIgnoreCaseAndIdNot(
            command.code(), academicUnitTypeId),
        "Academic unit type code already exists.");
    if (!unitType.getCode().equalsIgnoreCase(command.code())
        && academicUnitRepository.existsByAcademicUnitTypeId(academicUnitTypeId)) {
      throw new IllegalStateException(
          "Academic unit type code cannot change after academic units reference it.");
    }
    if (unitType.isLeafAllowed()
        && !command.leafAllowed()
        && (programmeRepository.existsByOwningAcademicUnitAcademicUnitTypeId(academicUnitTypeId)
            || academicModuleRepository.existsByOwningAcademicUnitAcademicUnitTypeId(
                academicUnitTypeId))) {
      throw new IllegalStateException(
          "Leaf ownership cannot be removed while units at this level own programmes or Modules.");
    }
    unitType.update(
        command.code(), command.name(), command.leafAllowed(), command.expectedVersion());
    return AcademicSetupSummaryMapper.unitType(academicUnitTypeRepository.saveAndFlush(unitType));
  }

  public AcademicUnitSummary createAcademicUnit(CreateAcademicUnit command) {
    requireUnique(
        !academicUnitRepository.existsByCodeIgnoreCase(command.code()),
        "Academic unit code already exists.");
    AcademicUnitType unitType = requireAcademicUnitType(command.academicUnitTypeId());
    requireActive(unitType.getStatus(), "Academic unit type");
    AcademicUnit parent =
        command.parentId() == null ? null : requireAcademicUnit(command.parentId());
    if (parent == null && unitType.getLevelOrder() != 1) {
      throw new IllegalArgumentException(
          "Only a level-one academic unit can be created without a parent.");
    }
    if (parent != null
        && unitType.getLevelOrder() != parent.getAcademicUnitType().getLevelOrder() + 1) {
      throw new IllegalArgumentException(
          "Academic unit type must immediately follow the parent unit type.");
    }
    if (parent != null
        && (programmeRepository.existsByOwningAcademicUnitId(parent.getId())
            || academicModuleRepository.existsByOwningAcademicUnitId(parent.getId()))) {
      throw new IllegalStateException(
          "The selected parent already owns programmes or Modules and cannot receive child units.");
    }
    return AcademicSetupSummaryMapper.unit(
        academicUnitRepository.saveAndFlush(
            new AcademicUnit(
                unitType,
                parent,
                command.code(),
                command.name(),
                command.legacyFacultyCode(),
                command.legacyDepartmentCode())));
  }

  public AcademicUnitSummary updateAcademicUnit(UUID academicUnitId, UpdateAcademicUnit command) {
    AcademicUnit academicUnit = requireAcademicUnit(academicUnitId);
    academicUnit.updateDescriptiveDetails(
        command.name(),
        command.legacyFacultyCode(),
        command.legacyDepartmentCode(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.unit(academicUnitRepository.saveAndFlush(academicUnit));
  }

  public AcademicYearSummary createAcademicYear(CreateAcademicYear command) {
    requireDateOrder(command.startDate(), command.endDate(), "academic year");
    requireUnique(
        !academicYearRepository.existsByNameIgnoreCase(command.name()),
        "Academic year name already exists.");
    requireUnique(
        !academicYearRepository.existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            command.endDate(), command.startDate()),
        "Academic year dates overlap another academic year.");
    return AcademicSetupSummaryMapper.year(
        academicYearRepository.saveAndFlush(
            new AcademicYear(command.name(), command.startDate(), command.endDate())));
  }

  public AcademicYearSummary updateAcademicYear(UUID academicYearId, UpdateAcademicYear command) {
    requireDateOrder(command.startDate(), command.endDate(), "academic year");
    AcademicYear academicYear = requireAcademicYear(academicYearId);
    requireUnique(
        !academicYearRepository.existsByNameIgnoreCaseAndIdNot(command.name(), academicYearId),
        "Academic year name already exists.");
    requireUnique(
        !academicYearRepository.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(
            command.endDate(), command.startDate(), academicYearId),
        "Academic year dates overlap another academic year.");
    boolean periodOutsideRange =
        academicPeriodRepository.findAllByAcademicYearId(academicYearId).stream()
            .anyMatch(
                period ->
                    period.getStartDate().isBefore(command.startDate())
                        || period.getEndDate().isAfter(command.endDate()));
    boolean intakeOutsideRange =
        intakeRepository.findAllByAcademicYearId(academicYearId).stream()
            .anyMatch(
                intake ->
                    intake.getStartsOn().isBefore(command.startDate())
                        || intake.getEndsOn().isAfter(command.endDate()));
    if (periodOutsideRange || intakeOutsideRange) {
      throw new IllegalArgumentException(
          "Academic year dates must continue to contain every linked academic period and intake.");
    }
    academicYear.update(
        command.name(),
        command.startDate(),
        command.endDate(),
        command.changeReason(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.year(academicYearRepository.saveAndFlush(academicYear));
  }

  public AcademicYearSummary openAcademicYear(UUID academicYearId, long expectedVersion) {
    AcademicYear academicYear = requireAcademicYear(academicYearId);
    academicYear.open(expectedVersion);
    return AcademicSetupSummaryMapper.year(academicYearRepository.saveAndFlush(academicYear));
  }

  public AcademicYearSummary closeAcademicYear(UUID academicYearId, long expectedVersion) {
    AcademicYear academicYear = requireAcademicYear(academicYearId);
    if (academicPeriodRepository.existsByAcademicYearIdAndStatus(
            academicYearId, CalendarStatus.OPEN)
        || intakeRepository.existsByAcademicYearIdAndStatus(academicYearId, CalendarStatus.OPEN)) {
      throw new IllegalStateException(
          "Close all open academic periods and intakes before closing the academic year.");
    }
    academicYear.close(expectedVersion);
    return AcademicSetupSummaryMapper.year(academicYearRepository.saveAndFlush(academicYear));
  }

  public AcademicPeriodTypeSummary createAcademicPeriodType(CreateAcademicPeriodType command) {
    requireUnique(
        !academicPeriodTypeRepository.existsByCodeIgnoreCase(command.code()),
        "Academic period type code already exists.");
    requireUnique(
        !academicPeriodTypeRepository.existsBySortOrder(command.sortOrder()),
        "Academic period type order already exists.");
    return AcademicSetupSummaryMapper.periodType(
        academicPeriodTypeRepository.saveAndFlush(
            new AcademicPeriodType(command.code(), command.name(), command.sortOrder())));
  }

  public AcademicPeriodTypeSummary updateAcademicPeriodType(
      UUID academicPeriodTypeId, UpdateAcademicPeriodType command) {
    AcademicPeriodType periodType = requireAcademicPeriodType(academicPeriodTypeId);
    requireUnique(
        !academicPeriodTypeRepository.existsByCodeIgnoreCaseAndIdNot(
            command.code(), academicPeriodTypeId),
        "Academic period type code already exists.");
    requireUnique(
        !academicPeriodTypeRepository.existsBySortOrderAndIdNot(
            command.sortOrder(), academicPeriodTypeId),
        "Academic period type order already exists.");
    if (!periodType.getCode().equalsIgnoreCase(command.code())
        && academicPeriodRepository.existsByAcademicPeriodTypeId(academicPeriodTypeId)) {
      throw new IllegalStateException(
          "Academic period type code cannot change after academic periods reference it.");
    }
    periodType.update(
        command.code(),
        command.name(),
        command.sortOrder(),
        command.changeReason(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.periodType(
        academicPeriodTypeRepository.saveAndFlush(periodType));
  }

  public AcademicPeriodSummary createAcademicPeriod(CreateAcademicPeriod command) {
    requireDateOrder(command.startDate(), command.endDate(), "academic period");
    requireUnique(
        !academicPeriodRepository.existsByCodeIgnoreCase(command.code()),
        "Academic period code already exists.");
    AcademicYear academicYear = requireAcademicYear(command.academicYearId());
    AcademicPeriodType periodType = requireAcademicPeriodType(command.academicPeriodTypeId());
    requireActive(periodType.getStatus(), "Academic period type");
    requireContainedDates(academicYear, command.startDate(), command.endDate(), "Academic period");
    return AcademicSetupSummaryMapper.period(
        academicPeriodRepository.saveAndFlush(
            new AcademicPeriod(
                academicYear,
                periodType,
                command.code(),
                command.name(),
                command.startDate(),
                command.endDate())));
  }

  public AcademicPeriodSummary updateAcademicPeriod(
      UUID academicPeriodId, UpdateAcademicPeriod command) {
    requireDateOrder(command.startDate(), command.endDate(), "academic period");
    AcademicPeriod academicPeriod = requireAcademicPeriod(academicPeriodId);
    requireUnique(
        !academicPeriodRepository.existsByCodeIgnoreCaseAndIdNot(command.code(), academicPeriodId),
        "Academic period code already exists.");
    AcademicYear academicYear = requireAcademicYear(command.academicYearId());
    AcademicPeriodType periodType = requireAcademicPeriodType(command.academicPeriodTypeId());
    requireActive(periodType.getStatus(), "Academic period type");
    requireContainedDates(academicYear, command.startDate(), command.endDate(), "Academic period");
    academicPeriod.update(
        academicYear,
        periodType,
        command.code(),
        command.name(),
        command.startDate(),
        command.endDate(),
        command.changeReason(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.period(academicPeriodRepository.saveAndFlush(academicPeriod));
  }

  public AcademicPeriodSummary openAcademicPeriod(UUID academicPeriodId, long expectedVersion) {
    AcademicPeriod academicPeriod = requireAcademicPeriod(academicPeriodId);
    requireOpenAcademicYear(academicPeriod.getAcademicYear(), "Academic period");
    academicPeriod.open(expectedVersion);
    return AcademicSetupSummaryMapper.period(academicPeriodRepository.saveAndFlush(academicPeriod));
  }

  public AcademicPeriodSummary closeAcademicPeriod(UUID academicPeriodId, long expectedVersion) {
    AcademicPeriod academicPeriod = requireAcademicPeriod(academicPeriodId);
    academicPeriod.close(expectedVersion);
    return AcademicSetupSummaryMapper.period(academicPeriodRepository.saveAndFlush(academicPeriod));
  }

  public IntakeSummary createIntake(CreateIntake command) {
    requireDateOrder(command.startsOn(), command.endsOn(), "intake");
    requireUnique(
        !intakeRepository.existsByCodeIgnoreCase(command.code()), "Intake code already exists.");
    AcademicYear academicYear = requireAcademicYear(command.academicYearId());
    requireContainedDates(academicYear, command.startsOn(), command.endsOn(), "Intake");
    IntakeEligibilityTargets eligibilityTargets =
        validateIntakeEligibilityTargets(command.programmeLevelIds(), command.programmeIds());
    Intake intake =
        intakeRepository.saveAndFlush(
            new Intake(
                academicYear,
                command.code(),
                command.name(),
                command.startsOn(),
                command.endsOn(),
                command.maximumProgrammeChoices(),
                command.offerAcceptanceDeadline(),
                command.registrationDate(),
                command.orientationDate(),
                command.commencementDate()));
    createIntakeEligibilityTargets(intake, eligibilityTargets);
    return intakeSummaryWithTargets(intake);
  }

  public IntakeSummary updateIntake(UUID intakeId, UpdateIntake command) {
    requireDateOrder(command.startsOn(), command.endsOn(), "intake");
    Intake intake = requireIntake(intakeId);
    requireUnique(
        !intakeRepository.existsByCodeIgnoreCaseAndIdNot(command.code(), intakeId),
        "Intake code already exists.");
    AcademicYear academicYear = requireAcademicYear(command.academicYearId());
    requireContainedDates(academicYear, command.startsOn(), command.endsOn(), "Intake");
    IntakeEligibilityTargets eligibilityTargets =
        validateIntakeEligibilityTargets(command.programmeLevelIds(), command.programmeIds());
    List<IntakeProgrammeLevelTarget> currentProgrammeLevelTargets =
        intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(intakeId);
    List<IntakeProgrammeTarget> currentProgrammeTargets =
        intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(intakeId);
    boolean eligibilityChanged =
        eligibilityChanged(
            currentProgrammeLevelTargets, currentProgrammeTargets, eligibilityTargets);
    if (eligibilityChanged && intake.getStatus() != CalendarStatus.DRAFT) {
      throw new IllegalStateException(
          "Programme eligibility can only be changed while the intake is in draft.");
    }
    intake.update(
        academicYear,
        command.code(),
        command.name(),
        command.startsOn(),
        command.endsOn(),
        command.maximumProgrammeChoices(),
        command.offerAcceptanceDeadline(),
        command.registrationDate(),
        command.orientationDate(),
        command.commencementDate(),
        command.changeReason(),
        command.expectedVersion());
    Intake savedIntake = intakeRepository.saveAndFlush(intake);
    if (eligibilityChanged) {
      synchronizeIntakeEligibilityTargets(
          savedIntake, currentProgrammeLevelTargets, currentProgrammeTargets, eligibilityTargets);
    }
    return intakeSummaryWithTargets(savedIntake);
  }

  public IntakeSummary openIntake(UUID intakeId, long expectedVersion) {
    Intake intake = requireIntake(intakeId);
    requireOpenAcademicYear(intake.getAcademicYear(), "Intake");
    if (intakeProgrammeLevelTargetRepository
        .findAllByIntakeIdWithProgrammeLevels(intakeId)
        .isEmpty()) {
      throw new IllegalStateException(
          "Select at least one Programme Level before opening the intake.");
    }
    intake.open(expectedVersion);
    return intakeSummaryWithTargets(intakeRepository.saveAndFlush(intake));
  }

  public IntakeSummary closeIntake(UUID intakeId, long expectedVersion) {
    Intake intake = requireIntake(intakeId);
    intake.close(expectedVersion);
    return intakeSummaryWithTargets(intakeRepository.saveAndFlush(intake));
  }

  public ProgrammeLevelSummary createProgrammeLevel(CreateProgrammeLevel command) {
    requireUnique(
        !programmeLevelRepository.existsByCodeIgnoreCase(command.code()),
        "Programme level code already exists.");
    requireUnique(
        !programmeLevelRepository.existsBySortOrder(command.sortOrder()),
        "Programme level order already exists.");
    return AcademicSetupSummaryMapper.programmeLevel(
        programmeLevelRepository.saveAndFlush(
            new ProgrammeLevel(command.code(), command.name(), command.sortOrder())));
  }

  public ProgrammeLevelSummary updateProgrammeLevel(
      UUID programmeLevelId, UpdateProgrammeLevel command) {
    ProgrammeLevel programmeLevel = requireProgrammeLevel(programmeLevelId);
    requireUnique(
        !programmeLevelRepository.existsBySortOrderAndIdNot(command.sortOrder(), programmeLevelId),
        "Programme level order already exists.");
    programmeLevel.update(command.name(), command.sortOrder(), command.expectedVersion());
    return AcademicSetupSummaryMapper.programmeLevel(
        programmeLevelRepository.saveAndFlush(programmeLevel));
  }

  public ProgrammeTypeSummary createProgrammeType(CreateProgrammeType command) {
    requireUnique(
        !programmeTypeRepository.existsByCodeIgnoreCase(command.code()),
        "Programme type code already exists.");
    return AcademicSetupSummaryMapper.programmeType(
        programmeTypeRepository.saveAndFlush(new ProgrammeType(command.code(), command.name())));
  }

  public ProgrammeTypeSummary updateProgrammeType(
      UUID programmeTypeId, UpdateProgrammeType command) {
    ProgrammeType programmeType = requireProgrammeType(programmeTypeId);
    programmeType.update(command.name(), command.expectedVersion());
    return AcademicSetupSummaryMapper.programmeType(
        programmeTypeRepository.saveAndFlush(programmeType));
  }

  public ProgrammeSummary createProgramme(CreateProgramme command) {
    requireUnique(
        !programmeRepository.existsByCodeIgnoreCase(command.code()),
        "Programme code already exists.");
    if (command.maximumDurationPeriods() < command.minimumDurationPeriods()) {
      throw new IllegalArgumentException(
          "Maximum programme duration cannot be shorter than minimum duration.");
    }
    AcademicUnit owner = requireLeafOwner(command.owningAcademicUnitId());
    ProgrammeType programmeType = requireProgrammeType(command.programmeTypeId());
    ProgrammeLevel programmeLevel = requireProgrammeLevel(command.programmeLevelId());
    requireActive(programmeType.getStatus(), "Programme type");
    requireActive(programmeLevel.getStatus(), "Programme level");
    return AcademicSetupSummaryMapper.programme(
        programmeRepository.saveAndFlush(
            new Programme(
                owner,
                programmeType,
                programmeLevel,
                command.code(),
                command.name(),
                command.awardName(),
                command.minimumDurationPeriods(),
                command.maximumDurationPeriods(),
                command.legacyProgrammeCode())));
  }

  public ProgrammeSummary updateProgramme(UUID programmeId, UpdateProgramme command) {
    if (command.maximumDurationPeriods() < command.minimumDurationPeriods()) {
      throw new IllegalArgumentException(
          "Maximum programme duration cannot be shorter than minimum duration.");
    }
    Programme programme = requireProgramme(programmeId);
    AcademicUnit owner = requireLeafOwner(command.owningAcademicUnitId());
    ProgrammeType programmeType = requireProgrammeType(command.programmeTypeId());
    ProgrammeLevel programmeLevel = requireProgrammeLevel(command.programmeLevelId());
    requireActive(programmeType.getStatus(), "Programme type");
    requireActive(programmeLevel.getStatus(), "Programme level");
    programme.update(
        owner,
        programmeType,
        programmeLevel,
        command.code(),
        command.name(),
        command.awardName(),
        command.minimumDurationPeriods(),
        command.maximumDurationPeriods(),
        command.legacyProgrammeCode(),
        command.changeReason(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.programme(programmeRepository.saveAndFlush(programme));
  }

  public ProgrammeSummary activateProgramme(UUID programmeId, long expectedVersion) {
    Programme programme = requireProgramme(programmeId);
    boolean hasApprovedVersion =
        programmeVersionRepository
            .findAllByProgrammeIdOrderByEffectiveFromDesc(programmeId)
            .stream()
            .anyMatch(version -> version.getStatus() == ProgrammeVersionStatus.APPROVED);
    if (!hasApprovedVersion) {
      throw new IllegalStateException(
          "Programme cannot be activated until it has an approved curriculum version.");
    }
    programme.activate(expectedVersion);
    return AcademicSetupSummaryMapper.programme(programmeRepository.saveAndFlush(programme));
  }

  @Transactional(readOnly = true)
  public ProgrammeHierarchyResolution resolveProgrammeHierarchy(UUID programmeId) {
    Programme programme = requireProgramme(programmeId);
    if (programme.getStatus() != AcademicOfferingStatus.ACTIVE) {
      throw new IllegalStateException(
          "Programme must be active before its recommendation hierarchy can be resolved.");
    }

    AcademicUnit owningAcademicUnit = programme.getOwningAcademicUnit();
    List<AcademicUnit> unitsFromOwnerToRoot = new ArrayList<>();
    Set<UUID> visitedUnitIds = new LinkedHashSet<>();
    AcademicUnit currentUnit = owningAcademicUnit;
    while (currentUnit != null) {
      if (!visitedUnitIds.add(currentUnit.getId())) {
        throw new IllegalStateException(
            "Academic unit hierarchy contains a cycle for programme " + programme.getCode() + ".");
      }
      requireActive(currentUnit.getStatus(), "Academic unit " + currentUnit.getCode());
      unitsFromOwnerToRoot.add(currentUnit);
      currentUnit = currentUnit.getParent();
    }
    if (unitsFromOwnerToRoot.isEmpty()) {
      throw new IllegalStateException("Programme has no owning academic unit hierarchy.");
    }

    Collections.reverse(unitsFromOwnerToRoot);
    AcademicUnit highestAcademicUnit = unitsFromOwnerToRoot.getFirst();
    return new ProgrammeHierarchyResolution(
        programme.getId(),
        programme.getCode(),
        programme.getName(),
        AcademicSetupSummaryMapper.unit(owningAcademicUnit),
        AcademicSetupSummaryMapper.unit(highestAcademicUnit),
        unitsFromOwnerToRoot.stream().map(AcademicSetupSummaryMapper::unit).toList());
  }

  public ProgrammeVersionSummary createProgrammeVersion(
      UUID programmeId, CreateProgrammeVersion command) {
    if (command.effectiveTo() != null) {
      requireDateOrder(command.effectiveFrom(), command.effectiveTo(), "programme version");
    }
    requireUnique(
        !programmeVersionRepository.existsByProgrammeIdAndVersionCodeIgnoreCase(
            programmeId, command.versionCode()),
        "Programme version code already exists for this programme.");
    ProgrammeVersion programmeVersion =
        programmeVersionRepository.saveAndFlush(
            new ProgrammeVersion(
                requireProgramme(programmeId),
                command.versionCode(),
                command.effectiveFrom(),
                command.effectiveTo()));
    return programmeVersionSummary(programmeVersion);
  }

  public AcademicModuleSummary createAcademicModule(CreateAcademicModule command) {
    requireUnique(
        !academicModuleRepository.existsByCodeIgnoreCase(command.code()),
        "Module code already exists.");
    AcademicUnit owner = requireLeafOwner(command.owningAcademicUnitId());
    return AcademicSetupSummaryMapper.module(
        academicModuleRepository.saveAndFlush(
            new AcademicModule(
                owner,
                command.code(),
                command.name(),
                command.description(),
                command.creditValue(),
                command.academicLevel(),
                command.legacyCourseCode())));
  }

  public AcademicModuleSummary updateAcademicModule(UUID moduleId, UpdateAcademicModule command) {
    AcademicModule academicModule = requireAcademicModule(moduleId);
    requireUnique(
        !academicModuleRepository.existsByCodeIgnoreCaseAndIdNot(command.code(), moduleId),
        "Module code already exists.");
    AcademicUnit owner = requireLeafOwner(command.owningAcademicUnitId());
    academicModule.update(
        owner,
        command.code(),
        command.name(),
        command.description(),
        command.creditValue(),
        command.academicLevel(),
        command.legacyCourseCode(),
        command.expectedVersion());
    return AcademicSetupSummaryMapper.module(academicModuleRepository.saveAndFlush(academicModule));
  }

  public AcademicModuleSummary activateAcademicModule(UUID moduleId, long expectedVersion) {
    AcademicModule academicModule = requireAcademicModule(moduleId);
    academicModule.activate(expectedVersion);
    return AcademicSetupSummaryMapper.module(academicModuleRepository.saveAndFlush(academicModule));
  }

  public CurriculumModuleSummary addCurriculumModule(
      UUID programmeVersionId, AddCurriculumModule command) {
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    requireAmendableCurriculum(programmeVersion);
    requireUnique(
        !curriculumModuleRepository.existsByProgrammeVersionIdAndAcademicModuleId(
            programmeVersionId, command.moduleId()),
        "Module already exists in this programme version.");
    requireUnique(
        !curriculumModuleRepository.existsByProgrammeVersionIdAndSortOrder(
            programmeVersionId, command.sortOrder()),
        "Curriculum sort order already exists in this programme version.");
    AcademicModule academicModule = requireAcademicModule(command.moduleId());
    if (academicModule.getStatus() != AcademicOfferingStatus.ACTIVE) {
      throw new IllegalStateException("Only an active Module can be added to a curriculum.");
    }
    if (command.periodNumber() > programmeVersion.getProgramme().getMaximumDurationPeriods()) {
      throw new IllegalArgumentException(
          "Curriculum period exceeds the programme maximum duration.");
    }
    String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
    String changeReason =
        command.changeReason() == null || command.changeReason().isBlank()
            ? "Module added to the governed curriculum."
            : command.changeReason().trim();
    EmhareRevisionContext.setRequestMetadata(correlationId, changeReason);
    try {
      return AcademicSetupSummaryMapper.curriculumModule(
          curriculumModuleRepository.saveAndFlush(
              new CurriculumModule(
                  programmeVersion,
                  academicModule,
                  command.periodNumber(),
                  command.moduleType(),
                  command.creditValue(),
                  command.minimumMarkRequired(),
                  command.sortOrder())));
    } finally {
      EmhareRevisionContext.setRequestMetadata(correlationId, null);
    }
  }

  public CurriculumModuleSummary updateCurriculumModule(
      UUID programmeVersionId, UUID curriculumModuleId, UpdateCurriculumModule command) {
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    requireAmendableCurriculum(programmeVersion);
    CurriculumModule curriculumModule =
        requireCurriculumModule(programmeVersionId, curriculumModuleId);
    requireUnique(
        !curriculumModuleRepository.existsByProgrammeVersionIdAndSortOrderAndIdNot(
            programmeVersionId, command.sortOrder(), curriculumModuleId),
        "Curriculum sort order already exists in this programme version.");
    if (command.periodNumber() > programmeVersion.getProgramme().getMaximumDurationPeriods()) {
      throw new IllegalArgumentException(
          "Curriculum period exceeds the programme maximum duration.");
    }
    String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
    EmhareRevisionContext.setRequestMetadata(correlationId, command.changeReason().trim());
    try {
      curriculumModule.updatePlacement(
          command.periodNumber(),
          command.moduleType(),
          command.creditValue(),
          command.minimumMarkRequired(),
          command.sortOrder(),
          command.expectedVersion());
      return AcademicSetupSummaryMapper.curriculumModule(
          curriculumModuleRepository.saveAndFlush(curriculumModule));
    } finally {
      EmhareRevisionContext.setRequestMetadata(correlationId, null);
    }
  }

  public ProgrammeVersionSummary approveProgrammeVersion(
      UUID programmeVersionId, long expectedVersion) {
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    List<CurriculumModule> curriculum =
        curriculumModuleRepository.findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(
            programmeVersionId);
    if (curriculum.isEmpty()) {
      throw new IllegalStateException(
          "Programme version must contain at least one Module before approval.");
    }
    if (curriculum.stream()
        .anyMatch(item -> item.getAcademicModule().getStatus() != AcademicOfferingStatus.ACTIVE)) {
      throw new IllegalStateException("Every curriculum Module must be active before approval.");
    }
    programmeVersion.approve(
        currentUserResolver.requireCurrentUser().auditUserId(), clock.instant(), expectedVersion);
    return programmeVersionSummary(programmeVersionRepository.saveAndFlush(programmeVersion));
  }

  public ProgrammeVersionSummary retireProgrammeVersion(
      UUID programmeVersionId, long expectedVersion, LocalDate retirementDate) {
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    programmeVersion.retire(retirementDate, expectedVersion);
    return programmeVersionSummary(programmeVersionRepository.saveAndFlush(programmeVersion));
  }

  @Transactional(readOnly = true)
  public AcademicSetupOverview overview() {
    return academicCatalogueQueryService.overview();
  }

  @Transactional(readOnly = true)
  public List<ProgrammeVersionSummary> programmeVersions(UUID programmeId) {
    return academicCatalogueQueryService.programmeVersions(programmeId);
  }

  @Transactional(readOnly = true)
  public List<CurriculumModuleSummary> curriculum(UUID programmeVersionId) {
    return academicCatalogueQueryService.curriculum(programmeVersionId);
  }

  @Transactional(readOnly = true)
  public AdmissionsCatalogue admissionsCatalogue(UUID academicYearId, UUID intakeId) {
    return academicCatalogueQueryService.admissionsCatalogue(academicYearId, intakeId);
  }

  @Transactional(readOnly = true)
  public List<AdmissionsIntakeOption> openAdmissionsIntakes() {
    return academicCatalogueQueryService.openAdmissionsIntakes();
  }

  @Transactional(readOnly = true)
  public AdmissionsIntakeOption admissionsIntake(UUID intakeId) {
    return academicCatalogueQueryService.admissionsIntake(intakeId);
  }

  public ProgrammeVersionSummary configureProgrammeEntryOptions(
      UUID programmeVersionId, ConfigureProgrammeEntryOptions request) {
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    programmeVersion.configureEntryOptions(
        request.minimumSelections(),
        request.maximumSelections(),
        request.options().stream()
            .map(
                option ->
                    new EntryOptionDefinition(
                        option.code(), option.name(), option.description(), option.sortOrder()))
            .toList(),
        request.expectedVersion());
    return programmeVersionSummary(programmeVersionRepository.saveAndFlush(programmeVersion));
  }

  @Transactional(readOnly = true)
  public RegistrationCatalogue registrationCatalogue(
      UUID academicPeriodId, UUID programmeVersionId, int periodNumber) {
    return academicCatalogueQueryService.registrationCatalogue(
        academicPeriodId, programmeVersionId, periodNumber);
  }

  private AcademicUnit requireLeafOwner(UUID academicUnitId) {
    AcademicUnit owner = requireAcademicUnit(academicUnitId);
    requireActive(owner.getStatus(), "Academic unit");
    if (!owner.getAcademicUnitType().isLeafAllowed()) {
      throw new IllegalArgumentException(
          "Selected academic unit type is not allowed to own programmes or Modules.");
    }
    if (academicUnitRepository.existsByParentId(academicUnitId)) {
      throw new IllegalArgumentException(
          "An academic unit with child units cannot own programmes or Modules.");
    }
    return owner;
  }

  private AcademicUnitType requireAcademicUnitType(UUID id) {
    return academicUnitTypeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic unit type was not found."));
  }

  private AcademicUnit requireAcademicUnit(UUID id) {
    return academicUnitRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic unit was not found."));
  }

  private AcademicYear requireAcademicYear(UUID id) {
    return academicYearRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic year was not found."));
  }

  private AcademicPeriodType requireAcademicPeriodType(UUID id) {
    return academicPeriodTypeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic period type was not found."));
  }

  private AcademicPeriod requireAcademicPeriod(UUID id) {
    return academicPeriodRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic period was not found."));
  }

  private Intake requireIntake(UUID id) {
    return intakeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Intake was not found."));
  }

  private ProgrammeType requireProgrammeType(UUID id) {
    return programmeTypeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme type was not found."));
  }

  private ProgrammeLevel requireProgrammeLevel(UUID id) {
    return programmeLevelRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme level was not found."));
  }

  private Programme requireProgramme(UUID id) {
    return programmeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme was not found."));
  }

  private ProgrammeVersion requireProgrammeVersion(UUID id) {
    return programmeVersionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme version was not found."));
  }

  private CurriculumModule requireCurriculumModule(
      UUID programmeVersionId, UUID curriculumModuleId) {
    CurriculumModule curriculumModule =
        curriculumModuleRepository
            .findById(curriculumModuleId)
            .orElseThrow(() -> new IllegalArgumentException("Curriculum Module was not found."));
    if (!curriculumModule.getProgrammeVersion().getId().equals(programmeVersionId)) {
      throw new IllegalArgumentException(
          "Curriculum Module does not belong to the selected programme version.");
    }
    return curriculumModule;
  }

  private void requireAmendableCurriculum(ProgrammeVersion programmeVersion) {
    if (programmeVersion.getStatus() == ProgrammeVersionStatus.RETIRED) {
      throw new IllegalStateException("A retired programme version cannot be amended.");
    }
  }

  private AcademicModule requireAcademicModule(UUID id) {
    return academicModuleRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Module was not found."));
  }

  private void requireContainedDates(
      AcademicYear year, LocalDate startDate, LocalDate endDate, String recordName) {
    if (startDate.isBefore(year.getStartDate()) || endDate.isAfter(year.getEndDate())) {
      throw new IllegalArgumentException(
          recordName + " dates must be contained by the academic year.");
    }
  }

  private void requireDateOrder(LocalDate startDate, LocalDate endDate, String recordName) {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException(
          "End date cannot be before start date for " + recordName + ".");
    }
  }

  private void requireUnique(boolean unique, String message) {
    if (!unique) {
      throw new IllegalArgumentException(message);
    }
  }

  private void requireActive(ReferenceStatus status, String recordName) {
    if (status != ReferenceStatus.ACTIVE) {
      throw new IllegalStateException(recordName + " must be active.");
    }
  }

  private void requireOpenAcademicYear(AcademicYear academicYear, String recordName) {
    if (academicYear.getStatus() != CalendarStatus.OPEN) {
      throw new IllegalStateException(
          recordName + " can only be opened while its academic year is open.");
    }
  }

  private IntakeSummary intakeSummaryWithTargets(Intake value) {
    return AcademicSetupSummaryMapper.intake(
        value,
        intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(value.getId()),
        intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(value.getId()));
  }

  private IntakeEligibilityTargets validateIntakeEligibilityTargets(
      List<UUID> programmeLevelIds, List<UUID> programmeIds) {
    if (programmeLevelIds == null || programmeLevelIds.isEmpty()) {
      throw new IllegalArgumentException("Select at least one Programme Level for the intake.");
    }
    if (programmeIds == null) {
      throw new IllegalArgumentException("Specific Programmes must be supplied as a list.");
    }
    Set<UUID> distinctProgrammeLevelIds = new LinkedHashSet<>(programmeLevelIds);
    Set<UUID> distinctProgrammeIds = new LinkedHashSet<>(programmeIds);
    if (distinctProgrammeLevelIds.size() != programmeLevelIds.size()) {
      throw new IllegalArgumentException(
          "The same Programme Level cannot be selected more than once.");
    }
    if (distinctProgrammeIds.size() != programmeIds.size()) {
      throw new IllegalArgumentException("The same Programme cannot be selected more than once.");
    }

    List<ProgrammeLevel> programmeLevels =
        programmeLevelRepository.findAllById(distinctProgrammeLevelIds);
    if (programmeLevels.size() != distinctProgrammeLevelIds.size()) {
      throw new IllegalArgumentException("One or more selected Programme Levels do not exist.");
    }
    programmeLevels.forEach(
        programmeLevel -> requireActive(programmeLevel.getStatus(), "Programme level"));

    List<Programme> programmes = programmeRepository.findAllById(distinctProgrammeIds);
    if (programmes.size() != distinctProgrammeIds.size()) {
      throw new IllegalArgumentException("One or more selected Programmes do not exist.");
    }
    Set<UUID> allowedProgrammeLevelIds =
        programmeLevels.stream().map(ProgrammeLevel::getId).collect(Collectors.toSet());
    for (Programme programme : programmes) {
      if (programme.getStatus() != AcademicOfferingStatus.ACTIVE) {
        throw new IllegalArgumentException("Only active Programmes can be assigned to an intake.");
      }
      if (!allowedProgrammeLevelIds.contains(programme.getProgrammeLevel().getId())) {
        throw new IllegalArgumentException(
            "Every specific Programme must belong to a selected Programme Level.");
      }
    }
    return new IntakeEligibilityTargets(programmeLevels, programmes);
  }

  private boolean eligibilityChanged(
      List<IntakeProgrammeLevelTarget> currentProgrammeLevelTargets,
      List<IntakeProgrammeTarget> currentProgrammeTargets,
      IntakeEligibilityTargets requestedTargets) {
    Set<UUID> currentProgrammeLevelIds =
        currentProgrammeLevelTargets.stream()
            .map(target -> target.getProgrammeLevel().getId())
            .collect(Collectors.toSet());
    Set<UUID> currentProgrammeIds =
        currentProgrammeTargets.stream()
            .map(target -> target.getProgramme().getId())
            .collect(Collectors.toSet());
    Set<UUID> requestedProgrammeLevelIds =
        requestedTargets.programmeLevels().stream()
            .map(ProgrammeLevel::getId)
            .collect(Collectors.toSet());
    Set<UUID> requestedProgrammeIds =
        requestedTargets.programmes().stream().map(Programme::getId).collect(Collectors.toSet());
    return !currentProgrammeLevelIds.equals(requestedProgrammeLevelIds)
        || !currentProgrammeIds.equals(requestedProgrammeIds);
  }

  private void createIntakeEligibilityTargets(
      Intake intake, IntakeEligibilityTargets requestedTargets) {
    intakeProgrammeLevelTargetRepository.saveAllAndFlush(
        requestedTargets.programmeLevels().stream()
            .map(programmeLevel -> new IntakeProgrammeLevelTarget(intake, programmeLevel))
            .toList());
    intakeProgrammeTargetRepository.saveAllAndFlush(
        requestedTargets.programmes().stream()
            .map(programme -> new IntakeProgrammeTarget(intake, programme))
            .toList());
  }

  private void synchronizeIntakeEligibilityTargets(
      Intake intake,
      List<IntakeProgrammeLevelTarget> currentProgrammeLevelTargets,
      List<IntakeProgrammeTarget> currentProgrammeTargets,
      IntakeEligibilityTargets requestedTargets) {
    Map<UUID, IntakeProgrammeTarget> currentProgrammesById =
        currentProgrammeTargets.stream()
            .collect(
                Collectors.toMap(target -> target.getProgramme().getId(), Function.identity()));
    Set<UUID> requestedProgrammeIds =
        requestedTargets.programmes().stream().map(Programme::getId).collect(Collectors.toSet());
    List<IntakeProgrammeTarget> removedProgrammeTargets =
        currentProgrammesById.entrySet().stream()
            .filter(entry -> !requestedProgrammeIds.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
    UUID actorUserId = currentUserResolver.requireCurrentUser().auditUserId();
    removedProgrammeTargets.forEach(target -> target.markDeleted(actorUserId));
    if (!removedProgrammeTargets.isEmpty()) {
      intakeProgrammeTargetRepository.saveAllAndFlush(removedProgrammeTargets);
    }

    Map<UUID, IntakeProgrammeLevelTarget> currentLevelsById =
        currentProgrammeLevelTargets.stream()
            .collect(
                Collectors.toMap(
                    target -> target.getProgrammeLevel().getId(), Function.identity()));
    Set<UUID> requestedProgrammeLevelIds =
        requestedTargets.programmeLevels().stream()
            .map(ProgrammeLevel::getId)
            .collect(Collectors.toSet());
    List<IntakeProgrammeLevelTarget> removedProgrammeLevelTargets =
        currentLevelsById.entrySet().stream()
            .filter(entry -> !requestedProgrammeLevelIds.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
    removedProgrammeLevelTargets.forEach(target -> target.markDeleted(actorUserId));
    if (!removedProgrammeLevelTargets.isEmpty()) {
      intakeProgrammeLevelTargetRepository.saveAllAndFlush(removedProgrammeLevelTargets);
    }

    List<IntakeProgrammeLevelTarget> addedProgrammeLevelTargets =
        requestedTargets.programmeLevels().stream()
            .filter(programmeLevel -> !currentLevelsById.containsKey(programmeLevel.getId()))
            .map(programmeLevel -> new IntakeProgrammeLevelTarget(intake, programmeLevel))
            .toList();
    if (!addedProgrammeLevelTargets.isEmpty()) {
      intakeProgrammeLevelTargetRepository.saveAllAndFlush(addedProgrammeLevelTargets);
    }

    List<IntakeProgrammeTarget> addedProgrammeTargets =
        requestedTargets.programmes().stream()
            .filter(programme -> !currentProgrammesById.containsKey(programme.getId()))
            .map(programme -> new IntakeProgrammeTarget(intake, programme))
            .toList();
    if (!addedProgrammeTargets.isEmpty()) {
      intakeProgrammeTargetRepository.saveAllAndFlush(addedProgrammeTargets);
    }
  }

  private record IntakeEligibilityTargets(
      List<ProgrammeLevel> programmeLevels, List<Programme> programmes) {}

  private ProgrammeVersionSummary programmeVersionSummary(ProgrammeVersion value) {
    List<CurriculumModule> curriculum =
        curriculumModuleRepository.findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(
            value.getId());
    BigDecimal totalCredits =
        curriculum.stream()
            .map(CurriculumModule::getCreditValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ProgrammeVersionSummary(
        value.getId(),
        value.getProgramme().getId(),
        value.getProgramme().getCode(),
        value.getVersionCode(),
        value.getEffectiveFrom(),
        value.getEffectiveTo(),
        value.getStatus(),
        value.getApprovedByUserId(),
        value.getApprovedAt(),
        value.getVersion(),
        curriculum.size(),
        totalCredits);
  }
}
