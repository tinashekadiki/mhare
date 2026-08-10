package zw.ac.uz.emhare.finance.billing;

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
