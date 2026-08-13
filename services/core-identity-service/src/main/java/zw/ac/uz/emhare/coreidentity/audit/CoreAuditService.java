package zw.ac.uz.emhare.coreidentity.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.coreidentity.audit.domain.model.AuditEvent;
import zw.ac.uz.emhare.coreidentity.audit.infrastructure.persistence.AuditEventRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.LoginEventRepository;

/** Records readable audit evidence and produces the Release 1 Core operations report. @author Tinashe K */
@Service
public class CoreAuditService {

    private final AuditEventRepository auditEventRepository;
    private final LoginEventRepository loginEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CoreAuditService(
            AuditEventRepository auditEventRepository,
            LoginEventRepository loginEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.loginEventRepository = loginEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AuditEventSummary record(
            UUID actorUserId,
            String eventType,
            String subjectType,
            UUID subjectId,
            String summary,
            Object before,
            Object after) {
        AuditEvent event = new AuditEvent(
                actorUserId,
                eventType,
                subjectType,
                subjectId,
                summary,
                serialize(before),
                serialize(after),
                clock.instant());
        return AuditEventSummary.from(auditEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<AuditEventSummary> recentEvents() {
        return auditEventRepository.findTop200ByDeletedAtIsNullOrderByOccurredAtDesc().stream()
                .map(AuditEventSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoreOperationalReport operationalReport(Map<String, Long> inventory) {
        Instant generatedAt = clock.instant();
        Instant since = generatedAt.minus(24, ChronoUnit.HOURS);
        return new CoreOperationalReport(
                generatedAt,
                Map.copyOf(inventory),
                loginEventRepository.countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(since),
                auditEventRepository.countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(since));
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Audit details could not be serialized.", exception);
        }
    }
}
