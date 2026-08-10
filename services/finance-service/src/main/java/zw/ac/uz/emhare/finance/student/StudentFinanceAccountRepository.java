package zw.ac.uz.emhare.finance.student;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentFinanceAccountRepository extends JpaRepository<StudentFinanceAccount, UUID> {
    Optional<StudentFinanceAccount> findByStudentIdAndDeletedAtIsNull(UUID studentId);
    Optional<StudentFinanceAccount> findByIdAndDeletedAtIsNull(UUID id);
    List<StudentFinanceAccount> findAllByDeletedAtIsNullOrderByStudentNumberAsc();
}
