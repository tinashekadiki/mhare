package zw.ac.uz.emhare.documentsreporting.integration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** @author Tinashe K */
@Configuration
@EnableScheduling
public class DocumentsReportingMessagingConfiguration {

    @Bean
    Declarables documentsReportingMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        List<Declarable> declarations = new ArrayList<>();
        declarations.add(eventsExchange);
        declarations.add(deadLetterExchange);
        declareRoute(
                declarations,
                eventsExchange,
                deadLetterExchange,
                EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_DOCUMENTS_QUEUE,
                EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_EVENT);
        declareRoute(
                declarations,
                eventsExchange,
                deadLetterExchange,
                EmhareMessagingTopology.PROGRESSION_DECISION_PUBLISHED_DOCUMENTS_QUEUE,
                EmhareMessagingTopology.PROGRESSION_DECISION_PUBLISHED_EVENT);
        declareRoute(declarations, eventsExchange, deadLetterExchange,
                EmhareMessagingTopology.OFFER_LETTER_REQUESTED_DOCUMENTS_QUEUE,
                EmhareMessagingTopology.OFFER_LETTER_REQUESTED_EVENT);
        return new Declarables(declarations);
    }

    private void declareRoute(
            List<Declarable> declarations,
            TopicExchange eventsExchange,
            DirectExchange deadLetterExchange,
            String queueName,
            String eventType) {
        String deadQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName))
                .build();
        Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
        declarations.add(queue);
        declarations.add(deadQueue);
        declarations.add(BindingBuilder.bind(queue).to(eventsExchange).with(eventType));
        declarations.add(BindingBuilder.bind(deadQueue).to(deadLetterExchange)
                .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }
}
