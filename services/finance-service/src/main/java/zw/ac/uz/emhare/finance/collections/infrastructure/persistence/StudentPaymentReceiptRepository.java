package zw.ac.uz.emhare.finance.collections.infrastructure.persistence;

import zw.ac.uz.emhare.finance.collections.domain.model.StudentPaymentReceipt;

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
public interface StudentPaymentReceiptRepository extends JpaRepository<StudentPaymentReceipt,UUID> {
    Optional<StudentPaymentReceipt> findByPaymentIdAndDeletedAtIsNull(UUID paymentId);
    List<StudentPaymentReceipt> findAllByDeletedAtIsNullOrderByIssuedAtDescReceiptNumberDesc();
}
