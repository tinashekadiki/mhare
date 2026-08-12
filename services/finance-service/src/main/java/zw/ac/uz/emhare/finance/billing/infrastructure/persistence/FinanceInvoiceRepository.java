package zw.ac.uz.emhare.finance.billing.infrastructure.persistence;

import zw.ac.uz.emhare.finance.billing.domain.model.FinanceInvoice;

import zw.ac.uz.emhare.finance.billing.*;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface FinanceInvoiceRepository extends JpaRepository<FinanceInvoice, UUID> {
    List<FinanceInvoice> findAllByDeletedAtIsNullOrderByPostedAtDescInvoiceNumberDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from FinanceInvoice invoice where invoice.id=:id and invoice.deletedAt is null")
    Optional<FinanceInvoice> findLockedByIdAndDeletedAtIsNull(@Param("id") UUID id);
}
