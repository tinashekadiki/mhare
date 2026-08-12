package zw.ac.uz.emhare.finance.student;

import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.infrastructure.persistence.StudentFinanceAccountRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;

/** @author Tinashe K */
class StudentFinanceAccountServiceTest {

    private final StudentFinanceAccountRepository repository = mock(StudentFinanceAccountRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-01-08T10:15:30Z"), ZoneOffset.UTC);
    private StudentFinanceAccountService service;

    @BeforeEach
    void setUp() {
        service = new StudentFinanceAccountService(repository, jdbcTemplate, clock);
    }

    @Test
    void returnsExistingAccountForAnExactReplay() {
        StudentFinanceAccountProvisioningRequestedEvent event = event();
        StudentFinanceAccount existing = new StudentFinanceAccount("SFA-000000001", event, clock.instant());
        when(repository.findByStudentIdAndDeletedAtIsNull(event.studentId()))
                .thenReturn(Optional.of(existing));

        StudentFinanceAccount result = service.ensureAccount(event);

        assertSame(existing, result);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsReplayWhoseOfferDoesNotMatchTheExistingStudentAccount() {
        StudentFinanceAccountProvisioningRequestedEvent original = event();
        StudentFinanceAccount existing = new StudentFinanceAccount(
                "SFA-000000001", original, clock.instant());
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
        verifyNoInteractions(jdbcTemplate);
    }

    private StudentFinanceAccountProvisioningRequestedEvent event() {
        return new StudentFinanceAccountProvisioningRequestedEvent(
                UUID.randomUUID(),
                StudentFinanceAccountProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                clock.instant(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "STU-2027-0000001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@example.test");
    }
}
