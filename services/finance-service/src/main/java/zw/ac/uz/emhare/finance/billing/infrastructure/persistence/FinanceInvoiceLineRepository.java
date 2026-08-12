package zw.ac.uz.emhare.finance.billing.infrastructure.persistence;

import zw.ac.uz.emhare.finance.billing.domain.model.FinanceInvoiceLine;

import zw.ac.uz.emhare.finance.billing.*;
import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface FinanceInvoiceLineRepository extends JpaRepository<FinanceInvoiceLine, UUID> {
    List<FinanceInvoiceLine> findAllByInvoiceIdAndDeletedAtIsNullOrderByLineNumberAsc(UUID invoiceId);
    Optional<FinanceInvoiceLine> findByIdAndDeletedAtIsNull(UUID id);
}
