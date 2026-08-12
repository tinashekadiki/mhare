package zw.ac.uz.emhare.admissions.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.AdmissionsOutboxEvent;

import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.admissions.integration.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface AdmissionsOutboxEventRepository extends JpaRepository<AdmissionsOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM integration_outbox
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY occurred_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 25
            """, nativeQuery = true)
    List<AdmissionsOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
