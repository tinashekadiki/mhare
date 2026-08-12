package zw.ac.uz.emhare.finance.student.infrastructure.persistence;

import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

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
