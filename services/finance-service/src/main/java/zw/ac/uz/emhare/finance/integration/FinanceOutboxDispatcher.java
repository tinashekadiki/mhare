package zw.ac.uz.emhare.finance.integration;

import zw.ac.uz.emhare.finance.infrastructure.messaging.model.FinanceOutboxEvent;
import zw.ac.uz.emhare.finance.infrastructure.persistence.messaging.FinanceOutboxEventRepository;

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
public class FinanceOutboxDispatcher {

    private static final long PUBLISH_CONFIRM_TIMEOUT_MILLISECONDS = 5_000L;

    private final FinanceOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public FinanceOutboxDispatcher(
            FinanceOutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${emhare.messaging.outbox-dispatch-interval-ms:1000}")
    @Transactional
    public void dispatchPendingEvents() {
        Instant dispatchStartedAt = clock.instant();
        List<FinanceOutboxEvent> events = outboxEventRepository.lockNextDispatchBatch(dispatchStartedAt);
        for (FinanceOutboxEvent event : events) {
            try {
                publishWithBrokerConfirmation(event);
                event.markPublished(clock.instant());
            } catch (AmqpException exception) {
                event.scheduleRetry(clock.instant(), exception);
            }
        }
    }

    private void publishWithBrokerConfirmation(FinanceOutboxEvent event) {
        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setHeader("source-service", "finance-service")
                .build();
        rabbitTemplate.invoke(operations -> {
            operations.send(EmhareMessagingTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(PUBLISH_CONFIRM_TIMEOUT_MILLISECONDS);
            return null;
        });
    }
}
