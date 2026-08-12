package zw.ac.uz.emhare.assessmentresults.roster;

import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;
import zw.ac.uz.emhare.assessmentresults.roster.infrastructure.persistence.AssessmentRosterEntryRepository;
import zw.ac.uz.emhare.assessmentresults.roster.infrastructure.persistence.RegistrationRosterImportRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;

/** @author Tinashe K */
class AssessmentRosterImportServiceTest {

    private final RegistrationRosterImportRepository importRepository =
            mock(RegistrationRosterImportRepository.class);
    private final AssessmentRosterEntryRepository entryRepository =
            mock(AssessmentRosterEntryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-08-20T09:00:00Z"), ZoneOffset.UTC);
    private AssessmentRosterImportService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentRosterImportService(importRepository, entryRepository, clock);
    }

    @Test
    void exactReplayReturnsTheExistingRosterWithoutDuplicatingEntries() {
        StudentRegistrationConfirmedEvent event = event();
        RegistrationRosterImport existing = new RegistrationRosterImport(event, clock.instant());
        when(importRepository.findByRegistrationSessionIdAndDeletedAtIsNull(event.registrationSessionId()))
                .thenReturn(Optional.of(existing));

        RegistrationRosterImport result = service.importConfirmedRegistration(event);

        assertSame(existing, result);
        verify(importRepository, never()).saveAndFlush(any());
        verifyNoInteractions(entryRepository);
    }

    @Test
    void conflictingEventForTheSameRegistrationIsRejected() {
        StudentRegistrationConfirmedEvent event = event();
        RegistrationRosterImport existing = new RegistrationRosterImport(event, clock.instant());
        StudentRegistrationConfirmedEvent conflicting = new StudentRegistrationConfirmedEvent(
                UUID.randomUUID(), event.schemaVersion(), event.occurredAt(), event.registrationSessionId(),
                UUID.randomUUID(), event.studentNumber(), event.programmeEnrolmentId(), event.programmeId(),
                event.programmeVersionId(), event.owningAcademicUnitId(), event.owningAcademicUnitCode(),
                event.owningAcademicUnitName(), event.programmeLevelId(), event.programmeLevelCode(),
                event.programmeLevelName(), event.academicPeriodId(), event.academicPeriodCode(),
                event.academicPeriodName(), event.academicPeriodStartsOn(), event.academicPeriodEndsOn(),
                event.programmePeriodNumber(), event.modules());
        when(importRepository.findByRegistrationSessionIdAndDeletedAtIsNull(event.registrationSessionId()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.importConfirmedRegistration(conflicting));
        verifyNoInteractions(entryRepository);
    }

    @Test
    void importsEveryRegisteredModuleIntoTheAssessmentRoster() {
        StudentRegistrationConfirmedEvent event = event();
        when(importRepository.findByRegistrationSessionIdAndDeletedAtIsNull(event.registrationSessionId()))
                .thenReturn(Optional.empty());
        when(importRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.importConfirmedRegistration(event);

        verify(entryRepository).saveAll(any());
    }

    private StudentRegistrationConfirmedEvent event() {
        return new StudentRegistrationConfirmedEvent(
                UUID.randomUUID(), StudentRegistrationConfirmedEvent.CURRENT_SCHEMA_VERSION, clock.instant(),
                UUID.randomUUID(), UUID.randomUUID(), "STU-2027-0000001", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BUS", "Business School",
                UUID.randomUUID(), "UG", "Undergraduate", UUID.randomUUID(), "2027-S1", "Semester 1",
                LocalDate.of(2027, 8, 16), LocalDate.of(2027, 12, 15), 1,
                List.of(new StudentRegistrationConfirmedEvent.RegisteredModule(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ACC101",
                        "Financial Accounting I", "COMPULSORY", new BigDecimal("12.00"),
                        new BigDecimal("50.00"))));
    }
}
