package zw.ac.uz.emhare.notifications;

import java.nio.charset.StandardCharsets;import java.time.Clock;
import org.springframework.amqp.AmqpException;import org.springframework.amqp.core.*;import org.springframework.amqp.rabbit.core.RabbitTemplate;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;import zw.ac.uz.emhare.notifications.infrastructure.persistence.NotificationDeliveryOutboxEventRepository;
/** @author Tinashe K */
@Component
public class NotificationDeliveryOutboxDispatcher{
    private final NotificationDeliveryOutboxEventRepository repository;private final RabbitTemplate rabbit;private final Clock clock;
    public NotificationDeliveryOutboxDispatcher(NotificationDeliveryOutboxEventRepository repository,RabbitTemplate rabbit,Clock clock){this.repository=repository;this.rabbit=rabbit;this.clock=clock;}
    @Scheduled(fixedDelayString="${emhare.messaging.outbox-dispatch-interval-ms:1000}")@Transactional
    public void dispatch(){for(var event:repository.lockNextDispatchBatch(clock.instant()))try{Message message=MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8)).setContentType("application/json").setMessageId(event.getId().toString()).setType(event.getEventType()).setHeader("source-service","notifications-service").build();rabbit.invoke(ops->{ops.send(EmhareMessagingTopology.EVENTS_EXCHANGE,event.getRoutingKey(),message);ops.waitForConfirmsOrDie(5000);return null;});event.markPublished(clock.instant());}catch(AmqpException exception){event.scheduleRetry(clock.instant(),exception);}}
}
