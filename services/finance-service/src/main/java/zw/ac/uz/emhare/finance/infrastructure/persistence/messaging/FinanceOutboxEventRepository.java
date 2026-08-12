package zw.ac.uz.emhare.finance.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.finance.infrastructure.messaging.model.FinanceOutboxEvent;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.integration.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface FinanceOutboxEventRepository extends JpaRepository<FinanceOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM integration_outbox
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY occurred_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 25
            """, nativeQuery = true)
    List<FinanceOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
