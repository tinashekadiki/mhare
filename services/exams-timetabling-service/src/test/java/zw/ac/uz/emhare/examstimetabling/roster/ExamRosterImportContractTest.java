package zw.ac.uz.emhare.examstimetabling.roster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent.RegisteredModule;
import zw.ac.uz.emhare.examstimetabling.ExamTestData.RegistrationEvidence;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamCandidateModule;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamRegistrationImport;
import zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence.ExamCandidateModuleRepository;
import zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence.ExamRegistrationImportRepository;

/**
 * @author Tinashe K
 */
class ExamRosterImportContractTest {
  private final ExamRegistrationImportRepository imports =
      mock(ExamRegistrationImportRepository.class);
  private final ExamCandidateModuleRepository candidates =
      mock(ExamCandidateModuleRepository.class);
  private final List<ExamCandidateModule> savedCandidates = new ArrayList<>();
  private final RegistrationEvidence evidence = new RegistrationEvidence();
  private final ExamRosterImportService service =
      new ExamRosterImportService(imports, candidates, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void capturePersistedRoster() {
    when(imports.saveAndFlush(any()))
        .thenAnswer(invocation -> identified(invocation.getArgument(0)));
    when(candidates.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ExamCandidateModule> records = invocation.getArgument(0);
              records.forEach(record -> savedCandidates.add(identified(record)));
              return records;
            });
  }

  @Test
  void confirmedEvidenceCreatesEligibleRosterAndExactReplayDoesNotDuplicateIt() {
    ExamRegistrationImport imported = service.importConfirmedRegistration(evidence.event());
    when(imports.findByRegistrationSessionIdAndDeletedAtIsNull(evidence.registrationId))
        .thenReturn(Optional.of(imported));
    assertSame(imported, service.importConfirmedRegistration(evidence.event()));
    assertAll(
        () -> assertEquals(evidence.studentId, imported.getStudentId()),
        () -> assertEquals("R260001", imported.getStudentNumber()),
        () -> assertEquals(evidence.registrationId, imported.getRegistrationSessionId()),
        () -> assertEquals(PERIOD, imported.getAcademicPeriodId()),
        () -> assertEquals("2026-S2", imported.getAcademicPeriodCode()),
        () -> assertEquals(NOW, imported.getImportedAt()));
    assertEquals(1, savedCandidates.size());
    ExamCandidateModule candidate = savedCandidates.getFirst();
    assertSame(imported, candidate.getRegistrationImport());
    assertEquals(evidence.modules.getFirst().moduleId(), candidate.getModuleId());
    assertEquals("CSC101", candidate.getModuleCode());
    assertEquals("CSC101 module", candidate.getModuleName());
    assertEquals(ExamCandidateModule.EligibilityStatus.ELIGIBLE, candidate.getEligibilityStatus());
    verify(imports, times(1)).saveAndFlush(any());
    verify(candidates, times(1)).saveAllAndFlush(any());
    when(candidates
            .findAllByRegistrationImportAcademicPeriodIdAndEligibilityStatusAndDeletedAtIsNull(
                PERIOD, ExamCandidateModule.EligibilityStatus.ELIGIBLE))
        .thenReturn(savedCandidates);
    assertEquals(
        savedCandidates, new ExamRosterQueryService(candidates).eligibleCandidates(PERIOD));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null-event",
        "event-id",
        "schema",
        "registration-id",
        "student-id",
        "student-number-null",
        "student-number-blank",
        "enrolment-id",
        "programme-id",
        "programme-version-id",
        "period-id",
        "period-code",
        "period-name",
        "start",
        "end",
        "date-order",
        "null-modules",
        "empty-modules"
      })
  void invalidRegistrationContractCannotWriteAnyRosterEvidence(String invalid) {
    switch (invalid) {
      case "event-id" -> evidence.eventId = null;
      case "schema" -> evidence.schemaVersion = 99;
      case "registration-id" -> evidence.registrationId = null;
      case "student-id" -> evidence.studentId = null;
      case "student-number-null" -> evidence.studentNumber = null;
      case "student-number-blank" -> evidence.studentNumber = " ";
      case "enrolment-id" -> evidence.enrolmentId = null;
      case "programme-id" -> evidence.programmeId = null;
      case "programme-version-id" -> evidence.programmeVersionId = null;
      case "period-id" -> evidence.periodId = null;
      case "period-code" -> evidence.periodCode = " ";
      case "period-name" -> evidence.periodName = null;
      case "start" -> evidence.startsOn = null;
      case "end" -> evidence.endsOn = null;
      case "date-order" -> evidence.endsOn = evidence.startsOn.minusDays(1);
      case "null-modules" -> evidence.modules = null;
      case "empty-modules" -> evidence.modules = List.of();
      default -> {}
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.importConfirmedRegistration(
                "null-event".equals(invalid) ? null : evidence.event()));
    verifyNoInteractions(imports, candidates);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "registration-module-id",
        "curriculum-id",
        "module-id",
        "module-code",
        "module-name",
        "duplicate-module",
        "duplicate-registration-module"
      })
  void malformedOrDuplicateModuleEvidenceCannotWritePartialRoster(String invalid) {
    RegisteredModule original = evidence.modules.getFirst();
    RegisteredModule changed =
        new RegisteredModule(
            "registration-module-id".equals(invalid) ? null : original.registrationModuleId(),
            "curriculum-id".equals(invalid) ? null : original.curriculumModuleId(),
            "module-id".equals(invalid) ? null : original.moduleId(),
            "module-code".equals(invalid) ? " " : original.moduleCode(),
            "module-name".equals(invalid) ? null : original.moduleName(),
            original.curriculumModuleType(),
            original.creditValue(),
            original.minimumMarkRequired());
    evidence.modules =
        switch (invalid) {
          case "duplicate-module" -> List.of(original, module(original.moduleId(), "CSC102"));
          case "duplicate-registration-module" ->
              List.of(
                  original,
                  new RegisteredModule(
                      original.registrationModuleId(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      "CSC102",
                      "Programming",
                      "COMPULSORY",
                      original.creditValue(),
                      null));
          default -> List.of(changed);
        };
    assertThrows(
        IllegalArgumentException.class,
        () -> service.importConfirmedRegistration(evidence.event()));
    verifyNoInteractions(imports, candidates);
  }

  @ParameterizedTest
  @ValueSource(strings = {"source-event", "student", "enrolment", "programme-version", "period"})
  void replayFromDifferentOwnershipEvidenceIsRejectedWithoutNewRows(String changed) {
    ExamRegistrationImport imported = new ExamRegistrationImport(evidence.event(), NOW);
    when(imports.findByRegistrationSessionIdAndDeletedAtIsNull(evidence.registrationId))
        .thenReturn(Optional.of(imported));
    switch (changed) {
      case "source-event" -> evidence.eventId = UUID.randomUUID();
      case "student" -> evidence.studentId = UUID.randomUUID();
      case "enrolment" -> evidence.enrolmentId = UUID.randomUUID();
      case "programme-version" -> evidence.programmeVersionId = UUID.randomUUID();
      case "period" -> evidence.periodId = UUID.randomUUID();
      default -> fail("Unknown ownership field");
    }
    assertThrows(
        IllegalStateException.class, () -> service.importConfirmedRegistration(evidence.event()));
    verify(imports, never()).saveAndFlush(any());
    verifyNoInteractions(candidates);
  }
}
