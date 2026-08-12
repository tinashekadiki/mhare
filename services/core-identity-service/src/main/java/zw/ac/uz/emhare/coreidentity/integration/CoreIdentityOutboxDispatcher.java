package zw.ac.uz.emhare.coreidentity.integration;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.CoreIdentityOutboxEvent;
import zw.ac.uz.emhare.coreidentity.infrastructure.persistence.messaging.CoreIdentityOutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** @author Tinashe K */
@Component
public class CoreIdentityOutboxDispatcher {
    private final CoreIdentityOutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public CoreIdentityOutboxDispatcher(
            CoreIdentityOutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString="${emhare.messaging.outbox-dispatch-interval-ms:1000}")
    @Transactional
    public void dispatchPendingEvents() {
        Instant now = clock.instant();
        List<CoreIdentityOutboxEvent> events = repository.lockNextDispatchBatch(now);
        for (CoreIdentityOutboxEvent event : events) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setMessageId(event.getId().toString())
                        .setType(event.getEventType())
                        .setHeader("source-service", "core-identity-service")
                        .build();
                rabbitTemplate.invoke(operations -> {
                    operations.send(
                            EmhareMessagingTopology.EVENTS_EXCHANGE,
                            event.getRoutingKey(),
                            message);
                    operations.waitForConfirmsOrDie(5_000L);
                    return null;
                });
                event.markPublished(clock.instant());
            } catch (AmqpException exception) {
                event.scheduleRetry(clock.instant(), exception);
            }
        }
    }
}
