package zw.ac.uz.emhare.coreidentity.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import zw.ac.uz.emhare.coreidentity.audit.domain.model.AuditEvent;
import zw.ac.uz.emhare.coreidentity.audit.infrastructure.persistence.AuditEventRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.LoginEventRepository;

/** Release 1 audit-event and Core-report regressions. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class CoreAuditServiceTest {

    @Mock private AuditEventRepository auditEventRepository;
    @Mock private LoginEventRepository loginEventRepository;

    private CoreAuditService service;
    private final Instant now = Instant.parse("2026-08-12T08:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CoreAuditService(
                auditEventRepository,
                loginEventRepository,
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void record_shouldPersistActorSubjectAndBeforeAfterEvidence() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            return event;
        });
        UUID actorUserId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        AuditEventSummary result = service.record(
                actorUserId,
                "CORE_USER_UPDATED",
                "PLATFORM_USER",
                subjectId,
                "Updated Core user profile.",
                Map.of("status", "INVITED"),
                Map.of("status", "ACTIVE"));

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());
        assertThat(result.actorUserId()).isEqualTo(actorUserId);
        assertThat(result.subjectId()).isEqualTo(subjectId);
        assertThat(eventCaptor.getValue().getBeforeJson()).contains("INVITED");
        assertThat(eventCaptor.getValue().getAfterJson()).contains("ACTIVE");
    }

    @Test
    void operationalReport_shouldCountLast24HoursAndPreserveInventory() {
        Instant since = now.minusSeconds(86_400);
        when(loginEventRepository.countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(since)).thenReturn(7L);
        when(auditEventRepository.countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(since)).thenReturn(12L);

        CoreOperationalReport report = service.operationalReport(Map.of("userCount", 20L));

        assertThat(report.loginSessionsLast24Hours()).isEqualTo(7L);
        assertThat(report.auditEventsLast24Hours()).isEqualTo(12L);
        assertThat(report.inventory()).containsEntry("userCount", 20L);
    }

    @Test
    void recentEvents_shouldMapPersistedAuditRecords() {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), "CORE_USER_UPDATED", "PLATFORM_USER",
                UUID.randomUUID(), "Updated user.", null, "{}", now);
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        when(auditEventRepository.findTop200ByDeletedAtIsNullOrderByOccurredAtDesc()).thenReturn(java.util.List.of(event));

        assertThat(service.recentEvents()).singleElement().extracting(AuditEventSummary::summary)
                .isEqualTo("Updated user.");
    }

    @Test
    void record_shouldAllowAbsentBeforeAndAfterSnapshots() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            return event;
        });

        AuditEventSummary result = service.record(
                UUID.randomUUID(), "CORE_USER_REGISTERED", "PLATFORM_USER", UUID.randomUUID(),
                "Registered user.", null, null);

        assertThat(result.beforeJson()).isNull();
        assertThat(result.afterJson()).isNull();
    }

    @Test
    void record_shouldRejectDetailsThatCannotBeSerialized() throws Exception {
        ObjectMapper failingObjectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any())).thenThrow(new JacksonException("broken") { });
        CoreAuditService failingService = new CoreAuditService(
                auditEventRepository, loginEventRepository, failingObjectMapper, Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> failingService.record(
                UUID.randomUUID(), "CORE_USER_UPDATED", "PLATFORM_USER", UUID.randomUUID(),
                "Updated user.", Map.of("before", true), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Audit details could not be serialized.");
    }
}
