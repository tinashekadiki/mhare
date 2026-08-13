package zw.ac.uz.emhare.finance.student;

import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;
import zw.ac.uz.emhare.finance.student.infrastructure.persistence.StudentFinanceAccountRepository;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.StudentFinanceAccountProvisioningRequestedEvent;

/** @author Tinashe K */
@Service
public class StudentFinanceAccountService {
    private final StudentFinanceAccountRepository repository;
    private final Clock clock;

    public StudentFinanceAccountService(
            StudentFinanceAccountRepository repository, Clock clock) {
        this.repository = repository;
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
                        new StudentFinanceAccount(event, clock.instant())));
    }
}
