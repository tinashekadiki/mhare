package zw.ac.uz.emhare.academicsetup.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.academicsetup.domain.model.*;
import zw.ac.uz.emhare.academicsetup.infrastructure.persistence.*;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** Isolated service repositories retaining real aggregate lifecycle state. @author Tinashe K */
abstract class AcademicServiceTestFixture {
  protected static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  protected static final LocalDate START = LocalDate.of(2026, 1, 1),
      END = LocalDate.of(2026, 12, 31);
  protected static final UUID ACTOR = UUID.randomUUID();
  protected final AcademicUnitTypeRepository unitTypes = mock(AcademicUnitTypeRepository.class);
  protected final AcademicUnitRepository units = mock(AcademicUnitRepository.class);
  protected final AcademicYearRepository years = mock(AcademicYearRepository.class);
  protected final AcademicPeriodTypeRepository periodTypes =
      mock(AcademicPeriodTypeRepository.class);
  protected final AcademicPeriodRepository periods = mock(AcademicPeriodRepository.class);
  protected final IntakeRepository intakes = mock(IntakeRepository.class);
  protected final IntakeProgrammeLevelTargetRepository intakeLevels =
      mock(IntakeProgrammeLevelTargetRepository.class);
  protected final IntakeProgrammeTargetRepository intakeProgrammes =
      mock(IntakeProgrammeTargetRepository.class);
  protected final ProgrammeLevelRepository levels = mock(ProgrammeLevelRepository.class);
  protected final ProgrammeTypeRepository types = mock(ProgrammeTypeRepository.class);
  protected final ProgrammeRepository programmes = mock(ProgrammeRepository.class);
  protected final ProgrammeVersionRepository versions = mock(ProgrammeVersionRepository.class);
  protected final AcademicModuleRepository modules = mock(AcademicModuleRepository.class);
  protected final CurriculumModuleRepository curricula = mock(CurriculumModuleRepository.class);
  protected final EmhareCurrentUserResolver users = mock(EmhareCurrentUserResolver.class);
  protected final Map<UUID, AcademicUnitType> storedUnitTypes = new LinkedHashMap<>();
  protected final Map<UUID, AcademicUnit> storedUnits = new LinkedHashMap<>();
  protected final Map<UUID, AcademicYear> storedYears = new LinkedHashMap<>();
  protected final Map<UUID, AcademicPeriodType> storedPeriodTypes = new LinkedHashMap<>();
  protected final Map<UUID, AcademicPeriod> storedPeriods = new LinkedHashMap<>();
  protected final Map<UUID, Intake> storedIntakes = new LinkedHashMap<>();
  protected final Map<UUID, ProgrammeLevel> storedLevels = new LinkedHashMap<>();
  protected final Map<UUID, ProgrammeType> storedTypes = new LinkedHashMap<>();
  protected final Map<UUID, Programme> storedProgrammes = new LinkedHashMap<>();
  protected final Map<UUID, ProgrammeVersion> storedVersions = new LinkedHashMap<>();
  protected final Map<UUID, AcademicModule> storedModules = new LinkedHashMap<>();
  protected final Map<UUID, CurriculumModule> storedCurricula = new LinkedHashMap<>();
  protected final List<IntakeProgrammeLevelTarget> storedIntakeLevels = new ArrayList<>();
  protected final List<IntakeProgrammeTarget> storedIntakeProgrammes = new ArrayList<>();
  protected AcademicSetupService service;
  protected AcademicCatalogueQueryService query;
  protected AcademicUnitType unitType;
  protected AcademicUnit owner;
  protected ProgrammeLevel level;
  protected ProgrammeType type;
  protected Programme programme;
  protected AcademicYear year;
  protected AcademicPeriodType periodType;

  @BeforeEach
  void configureAcademicServices() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    query =
        new AcademicCatalogueQueryService(
            unitTypes,
            units,
            years,
            periodTypes,
            periods,
            intakes,
            intakeLevels,
            intakeProgrammes,
            levels,
            types,
            programmes,
            versions,
            modules,
            curricula,
            clock);
    service =
        new AcademicSetupService(
            unitTypes,
            units,
            years,
            periodTypes,
            periods,
            intakes,
            intakeLevels,
            intakeProgrammes,
            levels,
            types,
            programmes,
            versions,
            modules,
            curricula,
            users,
            clock,
            query);
    when(users.requireCurrentUser())
        .thenReturn(
            new EmhareCurrentUser(
                UUID.randomUUID(),
                ACTOR,
                "operator@example.test",
                "operator",
                "Academic operator",
                Set.of("ACADEMIC_SETUP_ADMIN")));
    when(unitTypes.saveAndFlush(any()))
        .thenAnswer(inv -> save(inv.getArgument(0), storedUnitTypes));
    when(unitTypes.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedUnitTypes.get(inv.getArgument(0))));
    when(unitTypes.findAllByOrderByLevelOrderAsc())
        .thenAnswer(inv -> List.copyOf(storedUnitTypes.values()));
    when(units.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedUnits));
    when(units.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedUnits.get(inv.getArgument(0))));
    when(units.findAllByOrderByNameAsc()).thenAnswer(inv -> List.copyOf(storedUnits.values()));
    when(years.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedYears));
    when(years.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedYears.get(inv.getArgument(0))));
    when(years.findAllByOrderByStartDateDesc())
        .thenAnswer(inv -> List.copyOf(storedYears.values()));
    when(periodTypes.saveAndFlush(any()))
        .thenAnswer(inv -> save(inv.getArgument(0), storedPeriodTypes));
    when(periodTypes.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedPeriodTypes.get(inv.getArgument(0))));
    when(periodTypes.findAllByOrderBySortOrderAsc())
        .thenAnswer(inv -> List.copyOf(storedPeriodTypes.values()));
    when(periods.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedPeriods));
    when(periods.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedPeriods.get(inv.getArgument(0))));
    when(periods.findAllByOrderByStartDateDesc())
        .thenAnswer(inv -> List.copyOf(storedPeriods.values()));
    when(periods.findAllByAcademicYearId(any()))
        .thenAnswer(
            inv ->
                storedPeriods.values().stream()
                    .filter(value -> value.getAcademicYear().getId().equals(inv.getArgument(0)))
                    .toList());
    when(intakes.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedIntakes));
    when(intakes.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedIntakes.get(inv.getArgument(0))));
    when(intakes.findAllByOrderByStartsOnDesc())
        .thenAnswer(inv -> List.copyOf(storedIntakes.values()));
    when(intakes.findAllByAcademicYearId(any()))
        .thenAnswer(
            inv ->
                storedIntakes.values().stream()
                    .filter(value -> value.getAcademicYear().getId().equals(inv.getArgument(0)))
                    .toList());
    when(levels.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedLevels));
    when(levels.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedLevels.get(inv.getArgument(0))));
    when(levels.findAllById(any())).thenAnswer(inv -> selected(storedLevels, inv.getArgument(0)));
    when(levels.findAllByOrderBySortOrderAsc())
        .thenAnswer(inv -> List.copyOf(storedLevels.values()));
    when(types.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedTypes));
    when(types.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedTypes.get(inv.getArgument(0))));
    when(types.findAllByOrderByNameAsc()).thenAnswer(inv -> List.copyOf(storedTypes.values()));
    when(programmes.saveAndFlush(any()))
        .thenAnswer(inv -> save(inv.getArgument(0), storedProgrammes));
    when(programmes.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedProgrammes.get(inv.getArgument(0))));
    when(programmes.findAllById(any()))
        .thenAnswer(inv -> selected(storedProgrammes, inv.getArgument(0)));
    when(programmes.findAllByOrderByCodeAsc())
        .thenAnswer(inv -> List.copyOf(storedProgrammes.values()));
    when(versions.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedVersions));
    when(versions.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedVersions.get(inv.getArgument(0))));
    when(versions.findAllByProgrammeIdOrderByEffectiveFromDesc(any()))
        .thenAnswer(
            inv ->
                storedVersions.values().stream()
                    .filter(value -> value.getProgramme().getId().equals(inv.getArgument(0)))
                    .toList());
    when(versions.findAdmissionsCatalogueVersions(any()))
        .thenAnswer(
            inv ->
                storedVersions.values().stream()
                    .filter(
                        value ->
                            value.getStatus() == ProgrammeVersionStatus.APPROVED
                                && value.getProgramme().getStatus()
                                    == AcademicOfferingStatus.ACTIVE)
                    .toList());
    when(modules.saveAndFlush(any())).thenAnswer(inv -> save(inv.getArgument(0), storedModules));
    when(modules.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedModules.get(inv.getArgument(0))));
    when(modules.findAllByOrderByCodeAsc()).thenAnswer(inv -> List.copyOf(storedModules.values()));
    when(curricula.saveAndFlush(any()))
        .thenAnswer(inv -> save(inv.getArgument(0), storedCurricula));
    when(curricula.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(storedCurricula.get(inv.getArgument(0))));
    when(curricula.findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(any()))
        .thenAnswer(
            inv ->
                storedCurricula.values().stream()
                    .filter(value -> value.getProgrammeVersion().getId().equals(inv.getArgument(0)))
                    .toList());
    when(intakeLevels.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<IntakeProgrammeLevelTarget> targets = inv.getArgument(0);
              targets.forEach(
                  value -> {
                    if (!storedIntakeLevels.contains(value)) storedIntakeLevels.add(value);
                  });
              return targets;
            });
    when(intakeProgrammes.saveAllAndFlush(any()))
        .thenAnswer(
            inv -> {
              List<IntakeProgrammeTarget> targets = inv.getArgument(0);
              targets.forEach(
                  value -> {
                    if (!storedIntakeProgrammes.contains(value)) storedIntakeProgrammes.add(value);
                  });
              return targets;
            });
    when(intakeLevels.findAllByIntakeIdWithProgrammeLevels(any()))
        .thenAnswer(
            inv ->
                storedIntakeLevels.stream()
                    .filter(
                        value ->
                            !value.isDeleted()
                                && value.getIntake().getId().equals(inv.getArgument(0)))
                    .toList());
    when(intakeProgrammes.findAllByIntakeIdWithProgrammes(any()))
        .thenAnswer(
            inv ->
                storedIntakeProgrammes.stream()
                    .filter(
                        value ->
                            !value.isDeleted()
                                && value.getIntake().getId().equals(inv.getArgument(0)))
                    .toList());
    when(intakeLevels.findAllWithProgrammeLevels())
        .thenAnswer(
            inv -> storedIntakeLevels.stream().filter(value -> !value.isDeleted()).toList());
    when(intakeProgrammes.findAllWithProgrammes())
        .thenAnswer(
            inv -> storedIntakeProgrammes.stream().filter(value -> !value.isDeleted()).toList());
    unitType = save(new AcademicUnitType("SCHOOL", "School", 1, true), storedUnitTypes);
    owner = save(new AcademicUnit(unitType, null, "SCI", "Science", null, null), storedUnits);
    level = save(new ProgrammeLevel("UG", "Undergraduate", 1), storedLevels);
    type = save(new ProgrammeType("DEGREE", "Degree"), storedTypes);
    programme =
        save(
            new Programme(owner, type, level, "CSC", "Computing", "BSc", 6, 8, null),
            storedProgrammes);
    year = save(new AcademicYear("2026", START, END), storedYears);
    periodType = save(new AcademicPeriodType("SEMESTER", "Semester", 1), storedPeriodTypes);
  }

  @AfterEach
  void clearRevisionContext() {
    EmhareRevisionContext.clearRequestMetadata();
  }

  protected AcademicModule activeModule(String code) {
    AcademicModule module =
        save(
            new AcademicModule(
                owner, code, code, "Module description", new BigDecimal("15"), 1, null),
            storedModules);
    module.activate(0);
    return module;
  }

  protected ProgrammeVersion draftVersion(Programme selectedProgramme) {
    return save(new ProgrammeVersion(selectedProgramme, "V1", START, null), storedVersions);
  }

  protected Intake draftIntake(String code, LocalDate start, LocalDate end) {
    return save(new Intake(year, code, code, start, end, 3), storedIntakes);
  }

  protected <T extends AuditableEntity> T save(T value, Map<UUID, T> store) {
    if (value.getId() == null) ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    store.put(value.getId(), value);
    return value;
  }

  private <T> List<T> selected(Map<UUID, T> store, Iterable<UUID> ids) {
    List<T> values = new ArrayList<>();
    for (UUID id : ids) if (store.containsKey(id)) values.add(store.get(id));
    return values;
  }
}
