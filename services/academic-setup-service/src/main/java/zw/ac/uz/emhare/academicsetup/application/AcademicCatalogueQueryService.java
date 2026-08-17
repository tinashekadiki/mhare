package zw.ac.uz.emhare.academicsetup.application;

import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AcademicSetupOverview;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AdmissionsCatalogue;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AdmissionsIntakeOption;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.AdmissionsProgrammeOption;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.CurriculumModuleSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.ProgrammeVersionSummary;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.RegistrationCatalogue;
import static zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses.RegistrationModuleOption;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupResponses;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicOfferingStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriod;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;
import zw.ac.uz.emhare.academicsetup.domain.model.CalendarStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.CurriculumModule;
import zw.ac.uz.emhare.academicsetup.domain.model.Intake;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeLevelTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.Programme;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersionStatus;
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

/**
 * Read-only academic catalogues consumed by Admissions, Registration, and setup screens. @author
 * Tinashe K
 */
@Service
@Transactional(readOnly = true)
public class AcademicCatalogueQueryService {

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
  private final Clock clock;

  public AcademicCatalogueQueryService(
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
      Clock clock) {
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
    this.clock = clock;
  }

  public AcademicSetupOverview overview() {
    Map<UUID, List<IntakeProgrammeLevelTarget>> programmeLevelsByIntake =
        intakeProgrammeLevelTargetRepository.findAllWithProgrammeLevels().stream()
            .collect(Collectors.groupingBy(target -> target.getIntake().getId()));
    Map<UUID, List<IntakeProgrammeTarget>> programmesByIntake =
        intakeProgrammeTargetRepository.findAllWithProgrammes().stream()
            .collect(Collectors.groupingBy(target -> target.getIntake().getId()));
    return new AcademicSetupOverview(
        academicUnitTypeRepository.findAllByOrderByLevelOrderAsc().stream()
            .map(AcademicSetupSummaryMapper::unitType)
            .toList(),
        academicUnitRepository.findAllByOrderByNameAsc().stream()
            .map(AcademicSetupSummaryMapper::unit)
            .toList(),
        academicYearRepository.findAllByOrderByStartDateDesc().stream()
            .map(AcademicSetupSummaryMapper::year)
            .toList(),
        academicPeriodTypeRepository.findAllByOrderBySortOrderAsc().stream()
            .map(AcademicSetupSummaryMapper::periodType)
            .toList(),
        academicPeriodRepository.findAllByOrderByStartDateDesc().stream()
            .map(AcademicSetupSummaryMapper::period)
            .toList(),
        intakeRepository.findAllByOrderByStartsOnDesc().stream()
            .map(
                intake ->
                    AcademicSetupSummaryMapper.intake(
                        intake,
                        programmeLevelsByIntake.getOrDefault(intake.getId(), List.of()),
                        programmesByIntake.getOrDefault(intake.getId(), List.of())))
            .toList(),
        programmeLevelRepository.findAllByOrderBySortOrderAsc().stream()
            .map(AcademicSetupSummaryMapper::programmeLevel)
            .toList(),
        programmeTypeRepository.findAllByOrderByNameAsc().stream()
            .map(AcademicSetupSummaryMapper::programmeType)
            .toList(),
        programmeRepository.findAllByOrderByCodeAsc().stream()
            .map(AcademicSetupSummaryMapper::programme)
            .toList(),
        academicModuleRepository.findAllByOrderByCodeAsc().stream()
            .map(AcademicSetupSummaryMapper::module)
            .toList());
  }

  public List<ProgrammeVersionSummary> programmeVersions(UUID programmeId) {
    requireProgramme(programmeId);
    return programmeVersionRepository
        .findAllByProgrammeIdOrderByEffectiveFromDesc(programmeId)
        .stream()
        .map(this::programmeVersionSummary)
        .toList();
  }

  public List<CurriculumModuleSummary> curriculum(UUID programmeVersionId) {
    requireProgrammeVersion(programmeVersionId);
    return curriculumModuleRepository
        .findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(programmeVersionId)
        .stream()
        .map(AcademicSetupSummaryMapper::curriculumModule)
        .toList();
  }

  public AdmissionsCatalogue admissionsCatalogue(UUID academicYearId, UUID intakeId) {
    AcademicYear academicYear = requireAcademicYear(academicYearId);
    Intake intake = requireIntake(intakeId);
    if (academicYear.getStatus() != CalendarStatus.OPEN) {
      throw new IllegalStateException("Academic year is not open for admissions.");
    }
    if (!intake.getAcademicYear().getId().equals(academicYearId)) {
      throw new IllegalArgumentException("Intake does not belong to the selected academic year.");
    }
    if (intake.getStatus() != CalendarStatus.OPEN) {
      throw new IllegalStateException("Intake is not open for admissions.");
    }
    Set<UUID> programmeLevelIds =
        intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(intakeId).stream()
            .map(target -> target.getProgrammeLevel().getId())
            .collect(Collectors.toSet());
    if (programmeLevelIds.isEmpty()) {
      throw new IllegalStateException("Intake programme eligibility is not configured.");
    }
    Set<UUID> specificProgrammeIds =
        intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(intakeId).stream()
            .map(target -> target.getProgramme().getId())
            .collect(Collectors.toSet());
    List<AdmissionsProgrammeOption> programmes =
        programmeVersionRepository.findAdmissionsCatalogueVersions(intake.getStartsOn()).stream()
            .filter(
                version ->
                    specificProgrammeIds.isEmpty()
                        ? programmeLevelIds.contains(
                            version.getProgramme().getProgrammeLevel().getId())
                        : specificProgrammeIds.contains(version.getProgramme().getId()))
            .map(this::admissionsProgrammeOption)
            .toList();
    return new AdmissionsCatalogue(
        academicYear.getId(),
        academicYear.getName(),
        intake.getId(),
        intake.getCode(),
        intake.getName(),
        intake.getStartsOn(),
        intake.getEndsOn(),
        programmes);
  }

  public List<AdmissionsIntakeOption> openAdmissionsIntakes() {
    LocalDate today = LocalDate.now(clock);
    return intakeRepository.findAllByOrderByStartsOnDesc().stream()
        .filter(intake -> intake.getStatus() == CalendarStatus.OPEN)
        .filter(intake -> intake.getAcademicYear().getStatus() == CalendarStatus.OPEN)
        .filter(
            intake -> !today.isBefore(intake.getStartsOn()) && !today.isAfter(intake.getEndsOn()))
        .map(this::admissionsIntakeOption)
        .toList();
  }

  public AdmissionsIntakeOption admissionsIntake(UUID intakeId) {
    return admissionsIntakeOption(requireIntake(intakeId));
  }

  public RegistrationCatalogue registrationCatalogue(
      UUID academicPeriodId, UUID programmeVersionId, int periodNumber) {
    if (periodNumber < 1) {
      throw new IllegalArgumentException("Programme period number must be at least one.");
    }
    AcademicPeriod academicPeriod = requireAcademicPeriod(academicPeriodId);
    if (academicPeriod.getStatus() != CalendarStatus.OPEN) {
      throw new IllegalStateException("Academic period is not open for registration.");
    }
    ProgrammeVersion programmeVersion = requireProgrammeVersion(programmeVersionId);
    if (programmeVersion.getStatus() != ProgrammeVersionStatus.APPROVED) {
      throw new IllegalStateException(
          "Only an approved programme version can be used for registration.");
    }
    if (programmeVersion.getProgramme().getStatus() != AcademicOfferingStatus.ACTIVE) {
      throw new IllegalStateException("Programme is not active for registration.");
    }
    List<RegistrationModuleOption> modules =
        curriculumModuleRepository
            .findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(programmeVersionId)
            .stream()
            .filter(module -> module.getPeriodNumber() == periodNumber)
            .map(
                module ->
                    new RegistrationModuleOption(
                        module.getId(),
                        module.getAcademicModule().getId(),
                        module.getAcademicModule().getCode(),
                        module.getAcademicModule().getName(),
                        module.getModuleType(),
                        module.getCreditValue(),
                        module.getMinimumMarkRequired(),
                        module.getSortOrder()))
            .toList();
    if (modules.isEmpty()) {
      throw new IllegalStateException(
          "The approved programme version has no curriculum Modules for this period.");
    }
    Programme programme = programmeVersion.getProgramme();
    return new RegistrationCatalogue(
        academicPeriod.getId(),
        academicPeriod.getCode(),
        academicPeriod.getName(),
        academicPeriod.getStartDate(),
        academicPeriod.getEndDate(),
        programmeVersion.getId(),
        programme.getId(),
        programme.getCode(),
        programme.getName(),
        programmeVersion.getVersionCode(),
        programme.getOwningAcademicUnit().getId(),
        programme.getOwningAcademicUnit().getCode(),
        programme.getOwningAcademicUnit().getName(),
        programme.getProgrammeLevel().getId(),
        programme.getProgrammeLevel().getCode(),
        programme.getProgrammeLevel().getName(),
        periodNumber,
        modules);
  }

  private AdmissionsIntakeOption admissionsIntakeOption(Intake intake) {
    return new AdmissionsIntakeOption(
        intake.getId(),
        intake.getAcademicYear().getId(),
        intake.getAcademicYear().getName(),
        intake.getCode(),
        intake.getName(),
        intake.getStartsOn(),
        intake.getEndsOn(),
        intake.getOfferAcceptanceDeadline(),
        intake.getRegistrationDate(),
        intake.getOrientationDate(),
        intake.getCommencementDate(),
        intake.getStatus(),
        intake.getMaximumProgrammeChoices(),
        programmeVersionsForAdmissionsIntake(intake));
  }

  private List<AdmissionsProgrammeOption> programmeVersionsForAdmissionsIntake(Intake intake) {
    Set<UUID> programmeLevelIds =
        intakeProgrammeLevelTargetRepository
            .findAllByIntakeIdWithProgrammeLevels(intake.getId())
            .stream()
            .map(target -> target.getProgrammeLevel().getId())
            .collect(Collectors.toSet());
    if (programmeLevelIds.isEmpty()) return List.of();
    Set<UUID> specificProgrammeIds =
        intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(intake.getId()).stream()
            .map(target -> target.getProgramme().getId())
            .collect(Collectors.toSet());
    return programmeVersionRepository.findAdmissionsCatalogueVersions(intake.getStartsOn()).stream()
        .filter(
            version ->
                specificProgrammeIds.isEmpty()
                    ? programmeLevelIds.contains(version.getProgramme().getProgrammeLevel().getId())
                    : specificProgrammeIds.contains(version.getProgramme().getId()))
        .map(this::admissionsProgrammeOption)
        .toList();
  }

  private AdmissionsProgrammeOption admissionsProgrammeOption(ProgrammeVersion version) {
    Programme programme = version.getProgramme();
    return new AdmissionsProgrammeOption(
        programme.getId(),
        programme.getCode(),
        programme.getName(),
        programme.getAwardName(),
        version.getId(),
        version.getVersionCode(),
        programme.getOwningAcademicUnit().getId(),
        programme.getOwningAcademicUnit().getName(),
        programme.getMinimumDurationPeriods(),
        programme.getMaximumDurationPeriods(),
        programme.getProgrammeType().getId(),
        programme.getProgrammeType().getCode(),
        programme.getProgrammeType().getName(),
        programme.getProgrammeLevel().getId(),
        programme.getProgrammeLevel().getCode(),
        programme.getProgrammeLevel().getName(),
        version.getMinimumEntryOptionSelections(),
        version.getMaximumEntryOptionSelections(),
        version.getEntryOptions().stream()
            .map(
                option ->
                    new AcademicSetupResponses.ProgrammeEntryOptionSummary(
                        option.getId(),
                        option.getCode(),
                        option.getName(),
                        option.getDescription(),
                        option.getSortOrder()))
            .toList());
  }

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

  private AcademicYear requireAcademicYear(UUID id) {
    return academicYearRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic year not found."));
  }

  private AcademicPeriod requireAcademicPeriod(UUID id) {
    return academicPeriodRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Academic period not found."));
  }

  private Intake requireIntake(UUID id) {
    return intakeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Intake not found."));
  }

  private Programme requireProgramme(UUID id) {
    return programmeRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme not found."));
  }

  private ProgrammeVersion requireProgrammeVersion(UUID id) {
    return programmeVersionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Programme version not found."));
  }
}
