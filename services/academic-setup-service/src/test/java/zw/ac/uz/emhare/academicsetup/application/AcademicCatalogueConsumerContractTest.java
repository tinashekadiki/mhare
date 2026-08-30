package zw.ac.uz.emhare.academicsetup.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.academicsetup.domain.model.*;

/**
 * @author Tinashe K
 */
class AcademicCatalogueConsumerContractTest extends AcademicServiceTestFixture {
  @Test
  void admissionsCatalogueReturnsApprovedProgrammeAndOrderedEntryOptionSnapshots() {
    var version = prepareApprovedProgramme();
    var intake = openEligibleIntake();
    var catalogue = service.admissionsCatalogue(year.getId(), intake.getId());
    assertEquals(year.getId(), catalogue.academicYearId());
    assertEquals(intake.getId(), catalogue.intakeId());
    assertEquals(1, catalogue.programmes().size());
    var option = catalogue.programmes().getFirst();
    assertEquals(programme.getId(), option.programmeId());
    assertEquals(version.getId(), option.programmeVersionId());
    assertEquals(owner.getId(), option.owningAcademicUnitId());
    assertEquals(level.getId(), option.programmeLevelId());
    assertEquals(type.getId(), option.programmeTypeId());
    assertEquals(1, option.minimumEntryOptionSelections());
    assertEquals(2, option.maximumEntryOptionSelections());
    assertEquals(
        List.of("SE", "AI"), option.entryOptions().stream().map(value -> value.code()).toList());
    assertEquals(catalogue.programmes(), service.admissionsIntake(intake.getId()).programmes());
    assertEquals(intake.getId(), service.openAdmissionsIntakes().getFirst().intakeId());
  }

  @Test
  void admissionsEligibilityUsesProgrammeLevelUnlessAnExplicitWhitelistIsConfigured() {
    prepareApprovedProgramme();
    var postgraduate = save(new ProgrammeLevel("PG", "Postgraduate", 2), storedLevels);
    var masters =
        save(
            new Programme(owner, type, postgraduate, "MCSC", "MSc Computing", "MSc", 2, 4, null),
            storedProgrammes);
    masters.activate(0);
    var mastersVersion = draftVersion(masters);
    mastersVersion.approve(ACTOR, NOW, 0);
    var intake = openEligibleIntake();
    assertEquals(
        List.of(programme.getId()),
        service.admissionsCatalogue(year.getId(), intake.getId()).programmes().stream()
            .map(value -> value.programmeId())
            .toList());
    storedIntakeLevels.add(new IntakeProgrammeLevelTarget(intake, postgraduate));
    assertEquals(2, service.admissionsCatalogue(year.getId(), intake.getId()).programmes().size());
    storedIntakeProgrammes.add(new IntakeProgrammeTarget(intake, masters));
    assertEquals(
        List.of(masters.getId()),
        service.admissionsCatalogue(year.getId(), intake.getId()).programmes().stream()
            .map(value -> value.programmeId())
            .toList());
    assertEquals(
        List.of(masters.getId()),
        service.admissionsIntake(intake.getId()).programmes().stream()
            .map(value -> value.programmeId())
            .toList());
  }

  @ParameterizedTest
  @ValueSource(strings = {"closed-year", "other-year", "closed-intake", "unconfigured-targets"})
  void admissionsCatalogueFailsClosedForInvalidCalendarContext(String invalid) {
    var intake = openEligibleIntake();
    if ("closed-year".equals(invalid)) year.close(0);
    if ("closed-intake".equals(invalid)) intake.close(0);
    if ("unconfigured-targets".equals(invalid)) storedIntakeLevels.clear();
    var selectedYear = year;
    if ("other-year".equals(invalid)) {
      selectedYear =
          save(new AcademicYear("2027", START.plusYears(1), END.plusYears(1)), storedYears);
      selectedYear.open(0);
    }
    UUID yearId = selectedYear.getId();
    Class<? extends RuntimeException> expected =
        "other-year".equals(invalid) ? IllegalArgumentException.class : IllegalStateException.class;
    assertThrows(expected, () -> service.admissionsCatalogue(yearId, intake.getId()));
  }

  @Test
  void intakeWithoutEligibilityStillReturnsIdentityButNoProgrammeChoices() {
    prepareApprovedProgramme();
    var intake = draftIntake("EMPTY", START, END);
    var option = service.admissionsIntake(intake.getId());
    assertEquals("EMPTY", option.code());
    assertEquals(CalendarStatus.DRAFT, option.status());
    assertTrue(option.programmes().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"draft-intake", "closed-intake", "draft-year", "future-window", "past-window"})
  void openAdmissionsIntakesExcludeUnavailableWindows(String unavailable) {
    year.open(0);
    LocalDate today = LocalDate.ofInstant(NOW, java.time.ZoneOffset.UTC);
    var intake =
        draftIntake(
            "AUG",
            "future-window".equals(unavailable) ? today.plusDays(1) : START,
            "past-window".equals(unavailable) ? today.minusDays(1) : END);
    if (!"draft-intake".equals(unavailable)) intake.open(0);
    if ("closed-intake".equals(unavailable)) intake.close(0);
    if ("draft-year".equals(unavailable)) {
      var draftYear =
          save(new AcademicYear("2027", START.plusYears(1), END.plusYears(1)), storedYears);
      storedIntakes.clear();
      var different =
          save(new Intake(draftYear, "DRAFT-YEAR", "Draft year intake", START, END), storedIntakes);
      different.open(0);
    }
    assertTrue(service.openAdmissionsIntakes().isEmpty());
  }

  @Test
  void admissionsWindowIsInclusiveOnBothCalendarBoundaries() {
    year.open(0);
    LocalDate today = LocalDate.ofInstant(NOW, java.time.ZoneOffset.UTC);
    var intake = draftIntake("TODAY", today, today);
    intake.open(0);
    assertEquals(intake.getId(), service.openAdmissionsIntakes().getFirst().intakeId());
  }

  @Test
  void registrationCatalogueReturnsOnlyRequestedCurriculumPeriodWithAuthoritativeOwnership() {
    var version = prepareApprovedProgramme();
    var period =
        save(new AcademicPeriod(year, periodType, "S1", "Semester one", START, END), storedPeriods);
    period.open(0);
    var catalogue = service.registrationCatalogue(period.getId(), version.getId(), 1);
    assertEquals(owner.getId(), catalogue.owningAcademicUnitId());
    assertEquals(level.getId(), catalogue.programmeLevelId());
    assertEquals(version.getId(), catalogue.programmeVersionId());
    assertEquals(1, catalogue.periodNumber());
    assertEquals(1, catalogue.modules().size());
    assertEquals("CSC101", catalogue.modules().getFirst().moduleCode());
    assertEquals(CurriculumModuleType.COMPULSORY, catalogue.modules().getFirst().moduleType());
    assertEquals(new BigDecimal("15"), catalogue.modules().getFirst().creditValue());
    assertEquals(new BigDecimal("50"), catalogue.modules().getFirst().minimumMarkRequired());
    assertEquals(2, service.curriculum(version.getId()).size());
    assertEquals(
        new BigDecimal("35"),
        service.programmeVersions(programme.getId()).getFirst().totalCredits());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nonpositive-period",
        "closed-period",
        "draft-version",
        "inactive-programme",
        "no-modules"
      })
  void registrationCatalogueRejectsIncompleteOrUnapprovedContext(String invalid) {
    var version = draftVersion(programme);
    var period =
        save(new AcademicPeriod(year, periodType, "S1", "Semester one", START, END), storedPeriods);
    if (!"closed-period".equals(invalid)) period.open(0);
    if (!"draft-version".equals(invalid)) version.approve(ACTOR, NOW, 0);
    if (!"inactive-programme".equals(invalid)) programme.activate(0);
    int periodNumber = "nonpositive-period".equals(invalid) ? 0 : 1;
    Class<? extends RuntimeException> expected =
        "nonpositive-period".equals(invalid)
            ? IllegalArgumentException.class
            : IllegalStateException.class;
    assertThrows(
        expected,
        () -> service.registrationCatalogue(period.getId(), version.getId(), periodNumber));
  }

  @Test
  void setupOverviewPreservesLinkedIdentityAndTargetSummariesForOperators() {
    prepareApprovedProgramme();
    var intake = openEligibleIntake();
    storedIntakeProgrammes.add(new IntakeProgrammeTarget(intake, programme));
    var emptyIntake = draftIntake("EMPTY", START, END);
    save(new AcademicPeriod(year, periodType, "S1", "Semester one", START, END), storedPeriods);
    var overview = service.overview();
    assertEquals(owner.getId(), overview.academicUnits().getFirst().id());
    assertEquals(unitType.getId(), overview.academicUnitTypes().getFirst().id());
    assertEquals(year.getId(), overview.academicYears().getFirst().id());
    assertEquals(periodType.getId(), overview.academicPeriodTypes().getFirst().id());
    assertEquals(1, overview.academicPeriods().size());
    assertEquals(2, overview.modules().size());
    assertEquals(programme.getId(), overview.programmes().getFirst().id());
    assertEquals(level.getId(), overview.programmeLevels().getFirst().id());
    assertEquals(type.getId(), overview.programmeTypes().getFirst().id());
    assertEquals(
        programme.getId(), overview.intakes().getFirst().specificProgrammes().getFirst().id());
    assertTrue(
        overview.intakes().stream()
            .filter(value -> value.id().equals(emptyIntake.getId()))
            .findFirst()
            .orElseThrow()
            .programmeLevels()
            .isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"year", "intake", "programme", "version", "period"})
  void unknownCatalogueIdentifiersFailExplicitly(String resource) {
    UUID missing = UUID.randomUUID();
    switch (resource) {
      case "year" ->
          assertThrows(
              IllegalArgumentException.class, () -> service.admissionsCatalogue(missing, missing));
      case "intake" ->
          assertThrows(IllegalArgumentException.class, () -> service.admissionsIntake(missing));
      case "programme" ->
          assertThrows(IllegalArgumentException.class, () -> service.programmeVersions(missing));
      case "version" ->
          assertThrows(IllegalArgumentException.class, () -> service.curriculum(missing));
      case "period" ->
          assertThrows(
              IllegalArgumentException.class,
              () -> service.registrationCatalogue(missing, missing, 1));
    }
  }

  private ProgrammeVersion prepareApprovedProgramme() {
    var version = draftVersion(programme);
    version.configureEntryOptions(
        1,
        2,
        List.of(
            new ProgrammeVersion.EntryOptionDefinition(
                "AI", "Artificial Intelligence", "AI specialisation", 2),
            new ProgrammeVersion.EntryOptionDefinition("SE", "Software Engineering", null, 1)),
        0);
    var first = activeModule("CSC101");
    var second = activeModule("CSC201");
    save(
        new CurriculumModule(
            version,
            first,
            1,
            CurriculumModuleType.COMPULSORY,
            new BigDecimal("15"),
            new BigDecimal("50"),
            1),
        storedCurricula);
    save(
        new CurriculumModule(
            version, second, 2, CurriculumModuleType.ELECTIVE, new BigDecimal("20"), null, 2),
        storedCurricula);
    version.approve(ACTOR, NOW, 0);
    programme.activate(0);
    return version;
  }

  private Intake openEligibleIntake() {
    year.open(0);
    var intake = draftIntake("AUG", START, END);
    intake.open(0);
    storedIntakeLevels.add(new IntakeProgrammeLevelTarget(intake, level));
    return intake;
  }
}
