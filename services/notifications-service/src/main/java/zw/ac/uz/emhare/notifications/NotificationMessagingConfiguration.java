package zw.ac.uz.emhare.notifications;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** RabbitMQ topology for durable, recipient-resolved notification intents. @author Tinashe K */
@Configuration
public class NotificationMessagingConfiguration {

    @Bean
    Declarables notificationRequestedMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.NOTIFICATION_REQUESTED_QUEUE;
        String deadQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName))
                .build();
        Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
        return new Declarables(
                eventsExchange,
                deadLetterExchange,
                queue,
                deadQueue,
                BindingBuilder.bind(queue).to(eventsExchange).with(EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT),
                BindingBuilder.bind(deadQueue).to(deadLetterExchange).with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }
}
