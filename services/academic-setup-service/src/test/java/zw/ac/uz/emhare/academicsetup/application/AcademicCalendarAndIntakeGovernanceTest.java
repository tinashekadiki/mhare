package zw.ac.uz.emhare.academicsetup.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.academicsetup.api.model.AcademicSetupRequests.*;
import zw.ac.uz.emhare.academicsetup.domain.model.*;

/**
 * @author Tinashe K
 */
class AcademicCalendarAndIntakeGovernanceTest extends AcademicServiceTestFixture {
  @Test
  void academicYearCanBeCreatedCorrectedOpenedAndClosedWithAuditedReason() {
    var created =
        service.createAcademicYear(
            new CreateAcademicYear("2027", START.plusYears(1), END.plusYears(1)));
    assertEquals(CalendarStatus.DRAFT, created.status());
    var updated =
        service.updateAcademicYear(
            created.id(),
            new UpdateAcademicYear(
                "2027/28",
                START.plusYears(1),
                END.plusYears(1),
                "Senate approved calendar title",
                0));
    assertEquals("2027/28", updated.name());
    assertEquals("Senate approved calendar title", updated.changeReason());
    assertEquals(CalendarStatus.OPEN, service.openAcademicYear(created.id(), 0).status());
    assertEquals(CalendarStatus.CLOSED, service.closeAcademicYear(created.id(), 0).status());
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "overlap", "reversed"})
  void newAcademicYearRejectsDuplicateNamesOverlapsAndReversedDates(String invalid) {
    if ("name".equals(invalid)) when(years.existsByNameIgnoreCase("2027")).thenReturn(true);
    if ("overlap".equals(invalid))
      when(years.existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(END, START))
          .thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createAcademicYear(
                new CreateAcademicYear(
                    "2027", START, "reversed".equals(invalid) ? START.minusDays(1) : END)));
    verify(years, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"name", "overlap", "period-start", "period-end", "intake-start", "intake-end"})
  void academicYearCorrectionCannotInvalidateLinkedCalendarRecords(String invalid) {
    if ("name".equals(invalid))
      when(years.existsByNameIgnoreCaseAndIdNot("Corrected", year.getId())).thenReturn(true);
    if ("overlap".equals(invalid))
      when(years.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(
              END.minusDays(1), START.plusDays(1), year.getId()))
          .thenReturn(true);
    if (invalid.startsWith("period"))
      save(
          new AcademicPeriod(
              year,
              periodType,
              "S1",
              "Semester",
              "period-start".equals(invalid) ? START : START.plusDays(2),
              "period-end".equals(invalid) ? END : END.minusDays(2)),
          storedPeriods);
    if (invalid.startsWith("intake"))
      draftIntake(
          "AUG",
          "intake-start".equals(invalid) ? START : START.plusDays(2),
          "intake-end".equals(invalid) ? END : END.minusDays(2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateAcademicYear(
                year.getId(),
                new UpdateAcademicYear(
                    "Corrected",
                    START.plusDays(1),
                    END.minusDays(1),
                    "Senate correction authority",
                    0)));
    assertEquals(START, year.getStartDate());
  }

  @Test
  void openIntakePreventsYearClosure() {
    year.open(0);
    when(intakes.existsByAcademicYearIdAndStatus(year.getId(), CalendarStatus.OPEN))
        .thenReturn(true);
    assertThrows(IllegalStateException.class, () -> service.closeAcademicYear(year.getId(), 0));
    assertEquals(CalendarStatus.OPEN, year.getStatus());
  }

  @Test
  void periodTypesAndPeriodsSupportVersionedCorrectionAndControlledOpening() {
    var createdType =
        service.createAcademicPeriodType(new CreateAcademicPeriodType("TERM", "Term", 2));
    var updatedType =
        service.updateAcademicPeriodType(
            createdType.id(),
            new UpdateAcademicPeriodType(
                "BLOCK", "Teaching block", 3, "Approved calendar terminology", 0));
    assertEquals("BLOCK", updatedType.code());
    assertEquals(3, updatedType.sortOrder());
    var period =
        service.createAcademicPeriod(
            new CreateAcademicPeriod(
                year.getId(), createdType.id(), "2026-B1", "Block one", START, END));
    assertThrows(IllegalStateException.class, () -> service.openAcademicPeriod(period.id(), 0));
    var updated =
        service.updateAcademicPeriod(
            period.id(),
            new UpdateAcademicPeriod(
                year.getId(),
                periodType.getId(),
                "2026-S1",
                "Semester one",
                START.plusDays(1),
                END.minusDays(1),
                "Approved semester correction",
                0));
    assertEquals("2026-S1", updated.code());
    year.open(0);
    assertEquals(CalendarStatus.OPEN, service.openAcademicPeriod(period.id(), 0).status());
    assertEquals(CalendarStatus.CLOSED, service.closeAcademicPeriod(period.id(), 0).status());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"create-code", "create-order", "update-code", "update-order", "referenced-code"})
  void periodTypeChangesProtectUniquenessAndReferencedIdentity(String invalid) {
    switch (invalid) {
      case "create-code" -> when(periodTypes.existsByCodeIgnoreCase("TERM")).thenReturn(true);
      case "create-order" -> when(periodTypes.existsBySortOrder(2)).thenReturn(true);
      case "update-code" ->
          when(periodTypes.existsByCodeIgnoreCaseAndIdNot("TERM", periodType.getId()))
              .thenReturn(true);
      case "update-order" ->
          when(periodTypes.existsBySortOrderAndIdNot(2, periodType.getId())).thenReturn(true);
      case "referenced-code" ->
          when(periods.existsByAcademicPeriodTypeId(periodType.getId())).thenReturn(true);
    }
    if (invalid.startsWith("create"))
      assertThrows(
          IllegalArgumentException.class,
          () -> service.createAcademicPeriodType(new CreateAcademicPeriodType("TERM", "Term", 2)));
    else {
      Class<? extends RuntimeException> expected =
          "referenced-code".equals(invalid)
              ? IllegalStateException.class
              : IllegalArgumentException.class;
      assertThrows(
          expected,
          () ->
              service.updateAcademicPeriodType(
                  periodType.getId(),
                  new UpdateAcademicPeriodType("TERM", "Term", 2, "Senate approved change", 0)));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate", "before-year", "after-year", "reversed"})
  void academicPeriodsMustHaveUniqueCodeAndRemainWithinAcademicYear(String invalid) {
    if ("duplicate".equals(invalid)) when(periods.existsByCodeIgnoreCase("S1")).thenReturn(true);
    LocalDate start = "before-year".equals(invalid) ? START.minusDays(1) : START;
    LocalDate end =
        "after-year".equals(invalid)
            ? END.plusDays(1)
            : "reversed".equals(invalid) ? START.minusDays(1) : END;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createAcademicPeriod(
                new CreateAcademicPeriod(
                    year.getId(), periodType.getId(), "S1", "Semester one", start, end)));
  }

  @Test
  void periodUpdateRejectsDuplicateCodeAndRetainsSameReferencedTypeCode() {
    var period =
        save(new AcademicPeriod(year, periodType, "S1", "Semester one", START, END), storedPeriods);
    when(periods.existsByCodeIgnoreCaseAndIdNot("S2", period.getId())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateAcademicPeriod(
                period.getId(),
                new UpdateAcademicPeriod(
                    year.getId(),
                    periodType.getId(),
                    "S2",
                    "Semester two",
                    START,
                    END,
                    "Approved descriptive correction",
                    0)));
    when(periods.existsByAcademicPeriodTypeId(periodType.getId())).thenReturn(true);
    assertEquals(
        "SEMESTER",
        service
            .updateAcademicPeriodType(
                periodType.getId(),
                new UpdateAcademicPeriodType(
                    "semester", "Semester periods", 2, "Approved descriptive correction", 0))
            .code());
  }

  @Test
  void draftIntakeTargetsCanBeReplacedWithAuditedSoftDeletesAndThenLockedOnOpening() {
    programme.activate(0);
    var created =
        service.createIntake(createIntake(List.of(level.getId()), List.of(programme.getId())));
    assertEquals(programme.getId(), created.specificProgrammes().getFirst().id());
    assertFalse(created.allProgrammesInSelectedLevels());
    var previousProgrammeTarget = storedIntakeProgrammes.getFirst();
    var previousLevelTarget = storedIntakeLevels.getFirst();
    var postgraduate = save(new ProgrammeLevel("PG", "Postgraduate", 2), storedLevels);
    var masters =
        save(
            new Programme(owner, type, postgraduate, "MCSC", "MSc Computing", "MSc", 2, 4, null),
            storedProgrammes);
    masters.activate(0);
    var updated =
        service.updateIntake(
            created.id(), updateIntake(List.of(postgraduate.getId()), List.of(masters.getId()), 0));
    assertEquals(postgraduate.getId(), updated.programmeLevels().getFirst().id());
    assertEquals(masters.getId(), updated.specificProgrammes().getFirst().id());
    assertTrue(previousProgrammeTarget.isDeleted());
    assertEquals(ACTOR, previousProgrammeTarget.getDeletedByUserId());
    assertTrue(previousLevelTarget.isDeleted());
    assertEquals(ACTOR, previousLevelTarget.getDeletedByUserId());
    year.open(0);
    assertEquals(CalendarStatus.OPEN, service.openIntake(created.id(), 0).status());
    assertThrows(
        IllegalStateException.class,
        () ->
            service.updateIntake(
                created.id(), updateIntake(List.of(level.getId()), List.of(programme.getId()), 0)));
    assertEquals(CalendarStatus.CLOSED, service.closeIntake(created.id(), 0).status());
  }

  @Test
  void unchangedIntakeTargetsAreNotRewrittenDuringOpenCalendarCorrection() {
    var created = service.createIntake(createIntake(List.of(level.getId()), List.of()));
    assertTrue(created.allProgrammesInSelectedLevels());
    var target = storedIntakeLevels.getFirst();
    year.open(0);
    service.openIntake(created.id(), 0);
    clearInvocations(intakeLevels, intakeProgrammes);
    var corrected =
        service.updateIntake(created.id(), updateIntake(List.of(level.getId()), List.of(), 0));
    assertEquals("Corrected intake", corrected.name());
    assertSame(target, storedIntakeLevels.getFirst());
    verify(intakeLevels, never()).saveAllAndFlush(any());
    verify(intakeProgrammes, never()).saveAllAndFlush(any());
  }

  @Test
  void addingSpecificProgrammeKeepsExistingLevelAndRemovingWhitelistRestoresAllProgrammes() {
    programme.activate(0);
    var created = service.createIntake(createIntake(List.of(level.getId()), List.of()));
    var originalLevel = storedIntakeLevels.getFirst();
    service.updateIntake(
        created.id(), updateIntake(List.of(level.getId()), List.of(programme.getId()), 0));
    assertSame(originalLevel, storedIntakeLevels.getFirst());
    assertFalse(originalLevel.isDeleted());
    var cleared =
        service.updateIntake(created.id(), updateIntake(List.of(level.getId()), List.of(), 0));
    assertTrue(cleared.allProgrammesInSelectedLevels());
    assertTrue(storedIntakeProgrammes.getFirst().isDeleted());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null-levels",
        "empty-levels",
        "null-programmes",
        "duplicate-level",
        "duplicate-programme",
        "missing-level",
        "missing-programme",
        "inactive-programme",
        "wrong-programme-level"
      })
  void intakeCreationRequiresCompleteUniqueAndConsistentEligibilityTargets(String invalid) {
    if (!"inactive-programme".equals(invalid)) programme.activate(0);
    List<UUID> requestedLevels =
        "null-levels".equals(invalid)
            ? null
            : "empty-levels".equals(invalid)
                ? List.of()
                : "duplicate-level".equals(invalid)
                    ? List.of(level.getId(), level.getId())
                    : "missing-level".equals(invalid)
                        ? List.of(UUID.randomUUID())
                        : List.of(level.getId());
    List<UUID> requestedProgrammes =
        "null-programmes".equals(invalid)
            ? null
            : "duplicate-programme".equals(invalid)
                ? List.of(programme.getId(), programme.getId())
                : "missing-programme".equals(invalid)
                    ? List.of(UUID.randomUUID())
                    : List.of(programme.getId());
    if ("wrong-programme-level".equals(invalid)) {
      var postgraduate = save(new ProgrammeLevel("PG", "Postgraduate", 2), storedLevels);
      requestedLevels = List.of(postgraduate.getId());
    }
    var command = createIntake(requestedLevels, requestedProgrammes);
    assertThrows(IllegalArgumentException.class, () -> service.createIntake(command));
    assertTrue(storedIntakes.isEmpty());
  }

  @Test
  void intakeUniquenessAndOpeningRequireValidParentYear() {
    when(intakes.existsByCodeIgnoreCase("AUG")).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createIntake(createIntake(List.of(level.getId()), List.of())));
    when(intakes.existsByCodeIgnoreCase("AUG")).thenReturn(false);
    var created = service.createIntake(createIntake(List.of(level.getId()), List.of()));
    assertThrows(IllegalStateException.class, () -> service.openIntake(created.id(), 0));
    when(intakes.existsByCodeIgnoreCaseAndIdNot("AUG", created.id())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.updateIntake(created.id(), updateIntake(List.of(level.getId()), List.of(), 0)));
  }

  private CreateIntake createIntake(List<UUID> levelIds, List<UUID> programmeIds) {
    return new CreateIntake(
        year.getId(),
        "AUG",
        "August intake",
        START,
        END,
        NOW.plusSeconds(86400),
        END.minusDays(2),
        END.minusDays(1),
        END,
        3,
        levelIds,
        programmeIds);
  }

  private UpdateIntake updateIntake(List<UUID> levelIds, List<UUID> programmeIds, long version) {
    return new UpdateIntake(
        year.getId(),
        "AUG",
        "Corrected intake",
        START,
        END,
        NOW.plusSeconds(86400),
        END.minusDays(2),
        END.minusDays(1),
        END,
        4,
        levelIds,
        programmeIds,
        "Senate approved calendar correction",
        version);
  }
}
