package zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data persistence adapter. @author Tinashe K */
public interface FinanceFeeStructureRepository extends JpaRepository<FinanceFeeStructure,UUID> {
    List<FinanceFeeStructure> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<FinanceFeeStructure> findAllByStatusAndDeletedAtIsNull(FinanceFeeStructure.Status status);
    Optional<FinanceFeeStructure> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<FinanceFeeStructure> findLockedByIdAndDeletedAtIsNull(UUID id);
}
