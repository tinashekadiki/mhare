package zw.ac.uz.emhare.coreidentity.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface CoreIdentityOutboxEventRepository
        extends JpaRepository<CoreIdentityOutboxEvent, UUID> {
    @Query(
            value = """
                    SELECT * FROM integration_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= :now
                    ORDER BY occurred_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 25
                    """,
            nativeQuery = true)
    List<CoreIdentityOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
