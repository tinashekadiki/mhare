package zw.ac.uz.emhare.finance.billing.infrastructure.persistence;

import zw.ac.uz.emhare.finance.billing.domain.model.FinanceBillingEventScope;

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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

/** Spring Data persistence adapter. @author Tinashe K */
public interface FinanceBillingEventScopeRepository extends JpaRepository<FinanceBillingEventScope,UUID> {
    List<FinanceBillingEventScope> findAllByBillingEventIdAndDeletedAtIsNullOrderByScopeDimensionAsc(UUID billingEventId);
}
