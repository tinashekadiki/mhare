package zw.ac.uz.emhare.academicsetup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriod;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicPeriodType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnit;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnitType;
import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;
import zw.ac.uz.emhare.academicsetup.domain.model.CalendarStatus;
import zw.ac.uz.emhare.academicsetup.domain.model.Intake;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeLevelTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.IntakeProgrammeTarget;
import zw.ac.uz.emhare.academicsetup.domain.model.Programme;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeLevel;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeType;
import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeVersion;
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
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/**
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class AcademicSetupCalendarLifecycleServiceTest {

  @Mock AcademicUnitTypeRepository academicUnitTypeRepository;
  @Mock AcademicUnitRepository academicUnitRepository;
  @Mock AcademicYearRepository academicYearRepository;
  @Mock AcademicPeriodTypeRepository academicPeriodTypeRepository;
  @Mock AcademicPeriodRepository academicPeriodRepository;
  @Mock IntakeRepository intakeRepository;
  @Mock IntakeProgrammeLevelTargetRepository intakeProgrammeLevelTargetRepository;
  @Mock IntakeProgrammeTargetRepository intakeProgrammeTargetRepository;
  @Mock ProgrammeLevelRepository programmeLevelRepository;
  @Mock ProgrammeTypeRepository programmeTypeRepository;
  @Mock ProgrammeRepository programmeRepository;
  @Mock ProgrammeVersionRepository programmeVersionRepository;
  @Mock AcademicModuleRepository academicModuleRepository;
  @Mock CurriculumModuleRepository curriculumModuleRepository;
  @Mock EmhareCurrentUserResolver currentUserResolver;
  @Mock java.time.Clock clock;
  @Mock AcademicCatalogueQueryService academicCatalogueQueryService;

  @InjectMocks AcademicSetupService academicSetupService;

  private UUID academicYearId;
  private AcademicYear academicYear;

  @BeforeEach
  void setUp() {
    academicYearId = UUID.randomUUID();
    academicYear =
        new AcademicYear(
            "2028 Academic Year", LocalDate.parse("2028-01-01"), LocalDate.parse("2028-12-31"));
  }

  @Test
  void yearCannotCloseWhileAnAcademicPeriodIsOpen() {
    academicYear.open(0);
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(academicPeriodRepository.existsByAcademicYearIdAndStatus(
            academicYearId, CalendarStatus.OPEN))
        .thenReturn(true);

    assertThatThrownBy(() -> academicSetupService.closeAcademicYear(academicYearId, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Close all open academic periods and intakes before closing the academic year.");
    verify(academicYearRepository, never()).saveAndFlush(any());
  }

  @Test
  void hierarchyResolutionUsesHighestRootAndPreservesFullPath() {
    AcademicUnitType rootType = new AcademicUnitType("SCHOOL", "School", 1, false);
    AcademicUnitType leafType = new AcademicUnitType("DEPARTMENT", "Department", 2, true);
    ReflectionTestUtils.setField(rootType, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(leafType, "id", UUID.randomUUID());
    AcademicUnit root =
        new AcademicUnit(rootType, null, "CHS", "College of Health Sciences", null, null);
    AcademicUnit leaf = new AcademicUnit(leafType, root, "MED", "Medical School", null, null);
    ReflectionTestUtils.setField(root, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(leaf, "id", UUID.randomUUID());
    Programme programme = activeProgramme(leaf, "MBCHB");
    when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

    var hierarchy = academicSetupService.resolveProgrammeHierarchy(programme.getId());

    assertThat(hierarchy.owningAcademicUnit().id()).isEqualTo(leaf.getId());
    assertThat(hierarchy.highestAcademicUnit().id()).isEqualTo(root.getId());
    assertThat(hierarchy.ancestorPath())
        .extracting(unit -> unit.id())
        .containsExactly(root.getId(), leaf.getId());
  }

  @Test
  void oneLevelHierarchyUsesProgrammeOwnerAsRecommendationUnit() {
    AcademicUnitType rootLeafType = new AcademicUnitType("SCHOOL", "School", 1, true);
    ReflectionTestUtils.setField(rootLeafType, "id", UUID.randomUUID());
    AcademicUnit owner = new AcademicUnit(rootLeafType, null, "LAW", "School of Law", null, null);
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    Programme programme = activeProgramme(owner, "LLB");
    when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

    var hierarchy = academicSetupService.resolveProgrammeHierarchy(programme.getId());

    assertThat(hierarchy.highestAcademicUnit().id()).isEqualTo(owner.getId());
    assertThat(hierarchy.ancestorPath()).hasSize(1);
  }

  @Test
  void academicUnitTypeCanBeEditedWithOptimisticLocking() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 1, false);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));
    when(academicUnitTypeRepository.saveAndFlush(unitType)).thenReturn(unitType);

    var result =
        academicSetupService.updateAcademicUnitType(
            unitTypeId,
            new AcademicSetupRequests.UpdateAcademicUnitType(
                "COLLEGE", "Academic College", true, 0));

    assertThat(result.code()).isEqualTo("COLLEGE");
    assertThat(result.name()).isEqualTo("Academic College");
    assertThat(result.leafAllowed()).isTrue();
    assertThat(result.levelOrder()).isEqualTo(1);
  }

  @Test
  void academicUnitTypeCodeCannotChangeAfterUnitsReferenceIt() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("FACULTY", "Faculty", 1, false);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));
    when(academicUnitRepository.existsByAcademicUnitTypeId(unitTypeId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicUnitType(
                    unitTypeId,
                    new AcademicSetupRequests.UpdateAcademicUnitType(
                        "COLLEGE", "College", false, 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Academic unit type code cannot change after academic units reference it.");

    verify(academicUnitTypeRepository, never()).saveAndFlush(any());
  }

  @Test
  void academicUnitTypeEditRejectsADuplicateCode() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 1, false);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));
    when(academicUnitTypeRepository.existsByCodeIgnoreCaseAndIdNot("COLLEGE", unitTypeId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicUnitType(
                    unitTypeId,
                    new AcademicSetupRequests.UpdateAcademicUnitType(
                        "COLLEGE", "College", false, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Academic unit type code already exists.");

    verify(academicUnitTypeRepository, never()).saveAndFlush(any());
  }

  @Test
  void leafOwnershipCannotBeRemovedWhileProgrammesUseTheLevel() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 2, true);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));
    when(programmeRepository.existsByOwningAcademicUnitAcademicUnitTypeId(unitTypeId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicUnitType(
                    unitTypeId,
                    new AcademicSetupRequests.UpdateAcademicUnitType("SCHOOL", "School", false, 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Leaf ownership cannot be removed while units at this level own programmes or Modules.");

    verify(academicUnitTypeRepository, never()).saveAndFlush(any());
  }

  @Test
  void leafOwnershipCannotBeRemovedWhileModulesUseTheLevel() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 2, true);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));
    when(academicModuleRepository.existsByOwningAcademicUnitAcademicUnitTypeId(unitTypeId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicUnitType(
                    unitTypeId,
                    new AcademicSetupRequests.UpdateAcademicUnitType("SCHOOL", "School", false, 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Leaf ownership cannot be removed while units at this level own programmes or Modules.");

    verify(academicUnitTypeRepository, never()).saveAndFlush(any());
  }

  @Test
  void academicUnitTypeEditRejectsAStaleVersion() {
    UUID unitTypeId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 2, true);
    ReflectionTestUtils.setField(unitType, "id", unitTypeId);
    ReflectionTestUtils.setField(unitType, "version", 2L);
    when(academicUnitTypeRepository.findById(unitTypeId)).thenReturn(Optional.of(unitType));

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicUnitType(
                    unitTypeId,
                    new AcademicSetupRequests.UpdateAcademicUnitType(
                        "SCHOOL", "Updated School", true, 1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Academic unit type was changed by another user. Refresh before retrying.");

    verify(academicUnitTypeRepository, never()).saveAndFlush(any());
  }

  @Test
  void academicUnitDescriptiveDetailsCanBeCorrected() {
    UUID unitId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("FACULTY", "Faculty", 1, false);
    ReflectionTestUtils.setField(unitType, "id", UUID.randomUUID());
    AcademicUnit unit = new AcademicUnit(unitType, null, "SCI", "Science", "OLD-SCI", null);
    ReflectionTestUtils.setField(unit, "id", unitId);
    when(academicUnitRepository.findById(unitId)).thenReturn(Optional.of(unit));
    when(academicUnitRepository.saveAndFlush(unit)).thenReturn(unit);

    var result =
        academicSetupService.updateAcademicUnit(
            unitId,
            new AcademicSetupRequests.UpdateAcademicUnit("Faculty of Science", "SCI", null, 0));

    assertThat(result.name()).isEqualTo("Faculty of Science");
    assertThat(result.code()).isEqualTo("SCI");
    assertThat(result.legacyFacultyCode()).isEqualTo("SCI");
  }

  @Test
  void draftModuleCanBeEditedBeforeActivation() {
    UUID moduleId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("DEPARTMENT", "Department", 1, true);
    ReflectionTestUtils.setField(unitType, "id", UUID.randomUUID());
    AcademicUnit owner = new AcademicUnit(unitType, null, "CS", "Computer Science", null, null);
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    AcademicModule module =
        new AcademicModule(
            owner,
            "CSC101",
            "Communication Skills",
            "Original description",
            new BigDecimal("10.00"),
            1,
            null);
    ReflectionTestUtils.setField(module, "id", moduleId);
    when(academicModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));
    when(academicUnitRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
    when(academicModuleRepository.saveAndFlush(module)).thenReturn(module);

    var result =
        academicSetupService.updateAcademicModule(
            moduleId,
            new AcademicSetupRequests.UpdateAcademicModule(
                owner.getId(),
                "CSC102",
                "Professional Communication",
                "Updated description",
                new BigDecimal("12.00"),
                2,
                "LEG-CSC102",
                0));

    assertThat(result.code()).isEqualTo("CSC102");
    assertThat(result.name()).isEqualTo("Professional Communication");
    assertThat(result.creditValue()).isEqualByComparingTo("12.00");
    assertThat(result.academicLevel()).isEqualTo(2);
  }

  @Test
  void activeModuleRejectsIdentityChanges() {
    UUID moduleId = UUID.randomUUID();
    AcademicUnitType unitType = new AcademicUnitType("DEPARTMENT", "Department", 1, true);
    ReflectionTestUtils.setField(unitType, "id", UUID.randomUUID());
    AcademicUnit owner = new AcademicUnit(unitType, null, "CS", "Computer Science", null, null);
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    AcademicModule module =
        new AcademicModule(
            owner,
            "CSC101",
            "Communication Skills",
            "Original description",
            new BigDecimal("10.00"),
            1,
            null);
    ReflectionTestUtils.setField(module, "id", moduleId);
    module.activate(0);
    when(academicModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));
    when(academicUnitRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicModule(
                    moduleId,
                    new AcademicSetupRequests.UpdateAcademicModule(
                        owner.getId(),
                        "CSC102",
                        "Professional Communication",
                        "Updated description",
                        new BigDecimal("12.00"),
                        2,
                        null,
                        0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("An active Module's code and owning academic unit cannot be changed.");
  }

  @Test
  void programmeLevelAndTypeNamesCanBeCorrected() {
    UUID levelId = UUID.randomUUID();
    ProgrammeLevel level = new ProgrammeLevel("UNDERGRAD", "Undergraduate", 1);
    ReflectionTestUtils.setField(level, "id", levelId);
    when(programmeLevelRepository.findById(levelId)).thenReturn(Optional.of(level));
    when(programmeLevelRepository.saveAndFlush(level)).thenReturn(level);

    UUID typeId = UUID.randomUUID();
    ProgrammeType type = new ProgrammeType("DEGREE", "Degree");
    ReflectionTestUtils.setField(type, "id", typeId);
    when(programmeTypeRepository.findById(typeId)).thenReturn(Optional.of(type));
    when(programmeTypeRepository.saveAndFlush(type)).thenReturn(type);

    var updatedLevel =
        academicSetupService.updateProgrammeLevel(
            levelId, new AcademicSetupRequests.UpdateProgrammeLevel("Bachelor level", 2, 0));
    var updatedType =
        academicSetupService.updateProgrammeType(
            typeId, new AcademicSetupRequests.UpdateProgrammeType("Academic degree", 0));

    assertThat(updatedLevel.name()).isEqualTo("Bachelor level");
    assertThat(updatedLevel.sortOrder()).isEqualTo(2);
    assertThat(updatedType.name()).isEqualTo("Academic degree");
  }

  @Test
  void hierarchyResolutionRejectsAnInactiveAncestor() {
    AcademicUnitType rootType = new AcademicUnitType("COLLEGE", "College", 1, false);
    AcademicUnitType leafType = new AcademicUnitType("SCHOOL", "School", 2, true);
    ReflectionTestUtils.setField(rootType, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(leafType, "id", UUID.randomUUID());
    AcademicUnit inactiveRoot =
        new AcademicUnit(rootType, null, "SCI", "College of Science", null, null);
    AcademicUnit leaf =
        new AcademicUnit(leafType, inactiveRoot, "COMP", "School of Computing", null, null);
    ReflectionTestUtils.setField(inactiveRoot, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(
        inactiveRoot,
        "status",
        zw.ac.uz.emhare.academicsetup.domain.model.ReferenceStatus.INACTIVE);
    ReflectionTestUtils.setField(leaf, "id", UUID.randomUUID());
    Programme programme = activeProgramme(leaf, "BSC-CS");
    when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

    assertThatThrownBy(() -> academicSetupService.resolveProgrammeHierarchy(programme.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Academic unit SCI must be active");
  }

  @Test
  void hierarchyResolutionRejectsCycles() {
    AcademicUnitType unitType = new AcademicUnitType("SCHOOL", "School", 1, true);
    ReflectionTestUtils.setField(unitType, "id", UUID.randomUUID());
    AcademicUnit first = new AcademicUnit(unitType, null, "FIRST", "First School", null, null);
    AcademicUnit second = new AcademicUnit(unitType, first, "SECOND", "Second School", null, null);
    ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(first, "parent", second);
    Programme programme = activeProgramme(second, "BSC-CYCLE");
    when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

    assertThatThrownBy(() -> academicSetupService.resolveProgrammeHierarchy(programme.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hierarchy contains a cycle");
  }

  private Programme activeProgramme(AcademicUnit owner, String code) {
    ProgrammeType programmeType = new ProgrammeType("DEGREE", "Degree");
    ProgrammeLevel programmeLevel = new ProgrammeLevel("UG", "Undergraduate", 1);
    ReflectionTestUtils.setField(programmeType, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(programmeLevel, "id", UUID.randomUUID());
    Programme programme =
        new Programme(
            owner,
            programmeType,
            programmeLevel,
            code,
            code + " Programme",
            "Bachelor",
            6,
            8,
            null);
    ReflectionTestUtils.setField(programme, "id", UUID.randomUUID());
    programme.activate(0);
    return programme;
  }

  @Test
  void yearClosesAfterAllPeriodsAndIntakesAreClosed() {
    academicYear.open(0);
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(academicYearRepository.saveAndFlush(academicYear)).thenReturn(academicYear);

    var result = academicSetupService.closeAcademicYear(academicYearId, 0);

    assertThat(result.status()).isEqualTo(CalendarStatus.CLOSED);
  }

  @Test
  void academicPeriodCannotOpenUntilItsAcademicYearIsOpen() {
    UUID periodId = UUID.randomUUID();
    AcademicPeriod period =
        new AcademicPeriod(
            academicYear,
            new AcademicPeriodType("SEM", "Semester", 1),
            "2028-S1",
            "Semester 1",
            LocalDate.parse("2028-01-15"),
            LocalDate.parse("2028-06-30"));
    when(academicPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));

    assertThatThrownBy(() -> academicSetupService.openAcademicPeriod(periodId, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Academic period can only be opened while its academic year is open.");
    verify(academicPeriodRepository, never()).saveAndFlush(any());
  }

  @Test
  void openIntakeCanBeCorrectedWithOptimisticLockAndReason() {
    UUID intakeId = UUID.randomUUID();
    Intake intake =
        new Intake(
            academicYear,
            "JAN-2028",
            "January 2028 Intake",
            LocalDate.parse("2028-01-01"),
            LocalDate.parse("2028-03-31"));
    UUID programmeLevelId = UUID.randomUUID();
    ProgrammeLevel programmeLevel = new ProgrammeLevel("UG", "Undergraduate", 1);
    ReflectionTestUtils.setField(programmeLevel, "id", programmeLevelId);
    IntakeProgrammeLevelTarget programmeLevelTarget =
        new IntakeProgrammeLevelTarget(intake, programmeLevel);
    intake.open(0);
    when(intakeRepository.findById(intakeId)).thenReturn(Optional.of(intake));
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(programmeLevelRepository.findAllById(any())).thenReturn(java.util.List.of(programmeLevel));
    when(intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(intakeId))
        .thenReturn(java.util.List.of(programmeLevelTarget));
    when(intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(intakeId))
        .thenReturn(java.util.List.of());
    when(intakeRepository.saveAndFlush(intake)).thenReturn(intake);

    var result =
        academicSetupService.updateIntake(
            intakeId,
            new zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.UpdateIntake(
                academicYearId,
                "JAN-2028",
                "January and February 2028 Intake",
                LocalDate.parse("2028-01-02"),
                LocalDate.parse("2028-03-15"),
                java.util.List.of(programmeLevelId),
                java.util.List.of(),
                "Corrected the published application window.",
                0));

    assertThat(result.status()).isEqualTo(CalendarStatus.OPEN);
    assertThat(result.name()).isEqualTo("January and February 2028 Intake");
    assertThat(result.changeReason()).isEqualTo("Corrected the published application window.");
  }

  @Test
  void academicYearCannotBeShortenedPastLinkedPeriodDates() {
    AcademicPeriod linkedPeriod =
        new AcademicPeriod(
            academicYear,
            new AcademicPeriodType("SEM", "Semester", 1),
            "2028-S2",
            "Semester 2",
            LocalDate.parse("2028-07-01"),
            LocalDate.parse("2028-12-20"));
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(academicPeriodRepository.findAllByAcademicYearId(academicYearId))
        .thenReturn(java.util.List.of(linkedPeriod));

    assertThatThrownBy(
            () ->
                academicSetupService.updateAcademicYear(
                    academicYearId,
                    new zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests
                        .UpdateAcademicYear(
                        "2028 Academic Year",
                        LocalDate.parse("2028-01-01"),
                        LocalDate.parse("2028-11-30"),
                        "Corrected the academic year end date.",
                        0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Academic year dates must continue to contain every linked academic period and intake.");
    verify(academicYearRepository, never()).saveAndFlush(any());
  }

  @Test
  void intakeCannotOpenWithoutProgrammeLevelEligibility() {
    UUID intakeId = UUID.randomUUID();
    academicYear.open(0);
    Intake intake =
        new Intake(
            academicYear,
            "JAN-2028",
            "January 2028 Intake",
            LocalDate.parse("2028-01-01"),
            LocalDate.parse("2028-03-31"));
    when(intakeRepository.findById(intakeId)).thenReturn(Optional.of(intake));
    when(intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(intakeId))
        .thenReturn(java.util.List.of());

    assertThatThrownBy(() -> academicSetupService.openIntake(intakeId, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Select at least one Programme Level before opening the intake.");
    verify(intakeRepository, never()).saveAndFlush(any());
  }

  @Test
  void admissionsCatalogueUsesSpecificProgrammeWhitelistWhenConfigured() {
    UUID intakeId = UUID.randomUUID();
    ReflectionTestUtils.setField(academicYear, "id", academicYearId);
    academicYear.open(0);
    Intake intake =
        new Intake(
            academicYear,
            "JAN-2028",
            "January 2028 Intake",
            LocalDate.parse("2028-01-01"),
            LocalDate.parse("2028-03-31"));
    ReflectionTestUtils.setField(intake, "id", intakeId);
    intake.open(0);

    ProgrammeType programmeType = new ProgrammeType("DEGREE", "Degree");
    UUID programmeTypeId = UUID.randomUUID();
    ReflectionTestUtils.setField(programmeType, "id", programmeTypeId);
    AcademicUnit owner =
        new AcademicUnit(
            new AcademicUnitType("DEPARTMENT", "Department", 1, true),
            null,
            "COMPUTING",
            "Department of Computing",
            null,
            null);
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    ProgrammeLevel programmeLevel = new ProgrammeLevel("UG", "Undergraduate", 1);
    ReflectionTestUtils.setField(programmeLevel, "id", UUID.randomUUID());

    Programme selectedProgramme =
        new Programme(
            owner,
            programmeType,
            programmeLevel,
            "BSCIT",
            "Information Technology",
            "Bachelor of Science",
            6,
            8,
            null);
    Programme excludedProgramme =
        new Programme(
            owner,
            programmeType,
            programmeLevel,
            "BSCSE",
            "Software Engineering",
            "Bachelor of Science",
            6,
            8,
            null);
    UUID selectedProgrammeId = UUID.randomUUID();
    ReflectionTestUtils.setField(selectedProgramme, "id", selectedProgrammeId);
    ReflectionTestUtils.setField(excludedProgramme, "id", UUID.randomUUID());
    selectedProgramme.activate(0);
    excludedProgramme.activate(0);

    ProgrammeVersion selectedVersion =
        new ProgrammeVersion(selectedProgramme, "2028.1", LocalDate.parse("2028-01-01"), null);
    ProgrammeVersion excludedVersion =
        new ProgrammeVersion(excludedProgramme, "2028.1", LocalDate.parse("2028-01-01"), null);
    ReflectionTestUtils.setField(selectedVersion, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(excludedVersion, "id", UUID.randomUUID());
    selectedVersion.approve(UUID.randomUUID(), java.time.Instant.parse("2027-10-01T00:00:00Z"), 0);
    excludedVersion.approve(UUID.randomUUID(), java.time.Instant.parse("2027-10-01T00:00:00Z"), 0);

    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(intakeRepository.findById(intakeId)).thenReturn(Optional.of(intake));
    when(intakeProgrammeLevelTargetRepository.findAllByIntakeIdWithProgrammeLevels(intakeId))
        .thenReturn(java.util.List.of(new IntakeProgrammeLevelTarget(intake, programmeLevel)));
    when(intakeProgrammeTargetRepository.findAllByIntakeIdWithProgrammes(intakeId))
        .thenReturn(java.util.List.of(new IntakeProgrammeTarget(intake, selectedProgramme)));
    when(programmeVersionRepository.findAdmissionsCatalogueVersions(intake.getStartsOn()))
        .thenReturn(java.util.List.of(selectedVersion, excludedVersion));

    var catalogue = catalogueQueryService().admissionsCatalogue(academicYearId, intakeId);

    assertThat(catalogue.programmes())
        .extracting(programme -> programme.programmeId())
        .containsExactly(selectedProgrammeId);
  }

  private AcademicCatalogueQueryService catalogueQueryService() {
    return new AcademicCatalogueQueryService(
        academicUnitTypeRepository,
        academicUnitRepository,
        academicYearRepository,
        academicPeriodTypeRepository,
        academicPeriodRepository,
        intakeRepository,
        intakeProgrammeLevelTargetRepository,
        intakeProgrammeTargetRepository,
        programmeLevelRepository,
        programmeTypeRepository,
        programmeRepository,
        programmeVersionRepository,
        academicModuleRepository,
        curriculumModuleRepository,
        clock);
  }
}
