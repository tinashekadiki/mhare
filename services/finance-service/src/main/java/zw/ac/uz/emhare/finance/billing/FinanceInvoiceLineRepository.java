package zw.ac.uz.emhare.finance.billing;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface FinanceInvoiceLineRepository extends JpaRepository<FinanceInvoiceLine, UUID> {
    List<FinanceInvoiceLine> findAllByInvoiceIdAndDeletedAtIsNullOrderByLineNumberAsc(UUID invoiceId);
    Optional<FinanceInvoiceLine> findByIdAndDeletedAtIsNull(UUID id);
}
