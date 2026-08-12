package zw.ac.uz.emhare.finance.collections.infrastructure.persistence;

import zw.ac.uz.emhare.finance.collections.domain.model.StudentAccountPayment;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface StudentAccountPaymentRepository extends JpaRepository<StudentAccountPayment,UUID> {
    List<StudentAccountPayment> findAllByDeletedAtIsNullOrderByPaidAtDescPaymentNumberDesc();
    Optional<StudentAccountPayment> findByProviderCodeAndProviderTransactionReferenceAndDeletedAtIsNull(String providerCode,String reference);
    Optional<StudentAccountPayment> findByProviderEventFingerprintAndDeletedAtIsNull(String fingerprint);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select payment from StudentAccountPayment payment where payment.id=:id and payment.deletedAt is null") Optional<StudentAccountPayment> findLockedById(@Param("id") UUID id);
}
