package zw.ac.uz.emhare.common.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Exposes durable messaging backlog as health details and Prometheus gauges.
 *
 * @author Tinashe K
 */
@Component
@ConditionalOnProperty(name = "emhare.messaging.integration-enabled", havingValue = "true")
public class IntegrationMessagingMonitor implements HealthIndicator, MeterBinder {

    private static final Duration STALE_PENDING_THRESHOLD = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public IntegrationMessagingMonitor(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Health health() {
        long pendingCount = countOutboxEvents("PENDING");
        long deadCount = countOutboxEvents("DEAD");
        Instant oldestPendingAt = oldestPendingAt();
        Health.Builder health = deadCount > 0
                ? Health.down()
                : oldestPendingAt != null
                        && oldestPendingAt.isBefore(clock.instant().minus(STALE_PENDING_THRESHOLD))
                        ? Health.status(Status.OUT_OF_SERVICE)
                        : Health.up();
        return health
                .withDetail("pendingOutboxEvents", pendingCount)
                .withDetail("deadOutboxEvents", deadCount)
                .withDetail("oldestPendingAt", oldestPendingAt == null ? "none" : oldestPendingAt)
                .build();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("emhare.integration.outbox.pending", this, monitor -> monitor.safeCount("PENDING"))
                .description("Integration outbox events awaiting publication")
                .register(registry);
        Gauge.builder("emhare.integration.outbox.dead", this, monitor -> monitor.safeCount("DEAD"))
                .description("Integration outbox events requiring operator intervention")
                .register(registry);
    }

    private double safeCount(String status) {
        try {
            return countOutboxEvents(status);
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private long countOutboxEvents(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM integration_outbox WHERE status = ?",
                Long.class,
                status);
        return count == null ? 0 : count;
    }

    private Instant oldestPendingAt() {
        Timestamp oldestPendingTimestamp = jdbcTemplate.queryForObject(
                "SELECT min(occurred_at) FROM integration_outbox WHERE status = 'PENDING'",
                Timestamp.class);
        return oldestPendingTimestamp == null ? null : oldestPendingTimestamp.toInstant();
    }
}
