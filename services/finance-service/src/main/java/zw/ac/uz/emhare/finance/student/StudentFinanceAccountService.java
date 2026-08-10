package zw.ac.uz.emhare.finance.student;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;

/** @author Tinashe K */
@Service
public class StudentFinanceAccountService {
    private final StudentFinanceAccountRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public StudentFinanceAccountService(
            StudentFinanceAccountRepository repository, JdbcTemplate jdbcTemplate, Clock clock) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public StudentFinanceAccount ensureAccount(StudentFinanceAccountProvisioningRequestedEvent event) {
        return repository.findByStudentIdAndDeletedAtIsNull(event.studentId())
                .map(existing -> {
                    if (!existing.getSourceOfferId().equals(event.sourceOfferId())
                            || !existing.getUserId().equals(event.userId())
                            || !existing.getStudentNumber().equals(event.studentNumber())) {
                        throw new IllegalStateException(
                                "Existing finance account conflicts with the student provisioning request.");
                    }
                    return existing;
                })
                .orElseGet(() -> repository.saveAndFlush(
                        new StudentFinanceAccount(nextAccountNumber(), event, clock.instant())));
    }

    private String nextAccountNumber() {
        Long sequence = jdbcTemplate.queryForObject(
                "SELECT nextval('student_finance_account_number_sequence')", Long.class);
        if (sequence == null) {
            throw new IllegalStateException("Finance account sequence did not return a value.");
        }
        return "SFA-%09d".formatted(sequence);
    }
}
