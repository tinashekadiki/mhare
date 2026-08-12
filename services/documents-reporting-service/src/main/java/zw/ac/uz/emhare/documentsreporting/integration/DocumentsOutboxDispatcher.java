package zw.ac.uz.emhare.documentsreporting.integration;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsOutboxEvent;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsOutboxEventRepository;

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
public class DocumentsOutboxDispatcher {
    private static final long PUBLISH_CONFIRM_TIMEOUT_MILLISECONDS = 5_000L;

    private final DocumentsOutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public DocumentsOutboxDispatcher(
            DocumentsOutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${emhare.messaging.outbox-dispatch-interval-ms:1000}")
    @Transactional
    public void dispatchPendingEvents() {
        Instant startedAt = clock.instant();
        List<DocumentsOutboxEvent> events = repository.lockNextDispatchBatch(startedAt);
        for (DocumentsOutboxEvent event : events) {
            try {
                publish(event);
                event.markPublished(clock.instant());
            } catch (AmqpException exception) {
                event.scheduleRetry(clock.instant(), exception);
            }
        }
    }

    private void publish(DocumentsOutboxEvent event) {
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setHeader("source-service", "documents-reporting-service")
                .build();
        rabbitTemplate.invoke(operations -> {
            operations.send(EmhareMessagingTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(PUBLISH_CONFIRM_TIMEOUT_MILLISECONDS);
            return null;
        });
    }
}
