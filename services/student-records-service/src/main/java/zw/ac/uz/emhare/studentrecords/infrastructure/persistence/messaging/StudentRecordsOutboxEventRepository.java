package zw.ac.uz.emhare.studentrecords.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.StudentRecordsOutboxEvent;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.integration.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface StudentRecordsOutboxEventRepository extends JpaRepository<StudentRecordsOutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM integration_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY occurred_at, id FOR UPDATE SKIP LOCKED LIMIT 25
            """, nativeQuery = true)
    List<StudentRecordsOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
