package zw.ac.uz.emhare.finance.student;

import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.infrastructure.persistence.StudentFinanceAccountRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;

/** @author Tinashe K */
class StudentFinanceAccountServiceTest {

    private final StudentFinanceAccountRepository repository = mock(StudentFinanceAccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-01-08T10:15:30Z"), ZoneOffset.UTC);
    private StudentFinanceAccountService service;

    @BeforeEach
    void setUp() {
        service = new StudentFinanceAccountService(repository, clock);
    }

    @Test
    void returnsExistingAccountForAnExactReplay() {
        StudentFinanceAccountProvisioningRequestedEvent event = event();
        StudentFinanceAccount existing = new StudentFinanceAccount(event, clock.instant());
        when(repository.findByStudentIdAndDeletedAtIsNull(event.studentId()))
                .thenReturn(Optional.of(existing));

        StudentFinanceAccount result = service.ensureAccount(event);

        assertSame(existing, result);
    }

    @Test
    void rejectsReplayWhoseOfferDoesNotMatchTheExistingStudentAccount() {
        StudentFinanceAccountProvisioningRequestedEvent original = event();
        StudentFinanceAccount existing = new StudentFinanceAccount(original, clock.instant());
        StudentFinanceAccountProvisioningRequestedEvent conflicting =
                new StudentFinanceAccountProvisioningRequestedEvent(
                        UUID.randomUUID(),
                        StudentFinanceAccountProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                        clock.instant(),
                        original.conversionRequestId(),
                        original.studentId(),
                        original.studentNumber(),
                        original.userId(),
                        UUID.randomUUID(),
                        original.primaryEmail());
        when(repository.findByStudentIdAndDeletedAtIsNull(original.studentId()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.ensureAccount(conflicting));
    }

    @Test
    void usesTheRegistrationNumberAsTheFinanceAccountNumber() {
        StudentFinanceAccountProvisioningRequestedEvent event = event();
        when(repository.findByStudentIdAndDeletedAtIsNull(event.studentId()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(StudentFinanceAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StudentFinanceAccount account = service.ensureAccount(event);

        assertEquals(event.studentNumber(), account.getAccountNumber());
    }

    @Test
    void rejectsFinanceAccountProvisioningWithoutARegistrationNumber() {
        StudentFinanceAccountProvisioningRequestedEvent withoutNumber = eventWithStudentNumber(null);
        StudentFinanceAccountProvisioningRequestedEvent withBlankNumber = eventWithStudentNumber(" ");

        assertThrows(IllegalArgumentException.class,
                () -> new StudentFinanceAccount(withoutNumber, clock.instant()));
        assertThrows(IllegalArgumentException.class,
                () -> new StudentFinanceAccount(withBlankNumber, clock.instant()));
    }

    private StudentFinanceAccountProvisioningRequestedEvent event() {
        return eventWithStudentNumber("R270001A");
    }

    private StudentFinanceAccountProvisioningRequestedEvent eventWithStudentNumber(String studentNumber) {
        return new StudentFinanceAccountProvisioningRequestedEvent(
                UUID.randomUUID(),
                StudentFinanceAccountProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                clock.instant(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                studentNumber,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@example.test");
    }
}
