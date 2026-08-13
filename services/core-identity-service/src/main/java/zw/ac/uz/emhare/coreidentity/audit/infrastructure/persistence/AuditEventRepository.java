package zw.ac.uz.emhare.coreidentity.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.coreidentity.audit.domain.model.AuditEvent;

/** @author Tinashe K */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop200ByDeletedAtIsNullOrderByOccurredAtDesc();
    long countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(Instant occurredAt);
}
