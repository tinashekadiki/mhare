package zw.ac.uz.emhare.common.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IntegrationMessagingMonitorTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-08-07T10:00:00Z");

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IntegrationMessagingMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new IntegrationMessagingMonitor(
                jdbcTemplate,
                Clock.fixed(CURRENT_TIME, ZoneOffset.UTC));
    }

    @Test
    void health_shouldBeUpWhenTheOutboxHasNoPendingOrDeadEvents() {
        stubOutboxCounts(0L, 0L);
        stubOldestPendingEvent(null);

        Health health = monitor.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0L, health.getDetails().get("pendingOutboxEvents"));
        assertEquals(0L, health.getDetails().get("deadOutboxEvents"));
        assertEquals("none", health.getDetails().get("oldestPendingAt"));
    }

    @Test
    void health_shouldBeDownWhenAnEventRequiresOperatorIntervention() {
        stubOutboxCounts(0L, 1L);
        stubOldestPendingEvent(null);

        Health health = monitor.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(1L, health.getDetails().get("deadOutboxEvents"));
    }

    @Test
    void health_shouldBeOutOfServiceWhenPendingPublicationIsStale() {
        Instant staleEventTime = CURRENT_TIME.minusSeconds(301);
        stubOutboxCounts(1L, 0L);
        stubOldestPendingEvent(Timestamp.from(staleEventTime));

        Health health = monitor.health();

        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals(staleEventTime, health.getDetails().get("oldestPendingAt"));
    }

    @Test
    void bindTo_shouldExposePendingAndDeadEventGauges() {
        stubOutboxCounts(3L, 2L);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        monitor.bindTo(meterRegistry);

        assertEquals(3.0, meterRegistry.get("emhare.integration.outbox.pending").gauge().value());
        assertEquals(2.0, meterRegistry.get("emhare.integration.outbox.dead").gauge().value());
    }

    private void stubOutboxCounts(long pendingCount, long deadCount) {
        when(jdbcTemplate.queryForObject(
                        eq("SELECT count(*) FROM integration_outbox WHERE status = ?"),
                        eq(Long.class),
                        eq("PENDING")))
                .thenReturn(pendingCount);
        when(jdbcTemplate.queryForObject(
                        eq("SELECT count(*) FROM integration_outbox WHERE status = ?"),
                        eq(Long.class),
                        eq("DEAD")))
                .thenReturn(deadCount);
    }

    private void stubOldestPendingEvent(Timestamp oldestPendingEventTime) {
        when(jdbcTemplate.queryForObject(
                        eq("SELECT min(occurred_at) FROM integration_outbox WHERE status = 'PENDING'"),
                        eq(Timestamp.class)))
                .thenReturn(oldestPendingEventTime);
    }
}
