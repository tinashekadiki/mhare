package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsOutboxEvent;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.integration.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface DocumentsOutboxEventRepository extends JpaRepository<DocumentsOutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM integration_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY occurred_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 25
            """, nativeQuery = true)
    List<DocumentsOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
