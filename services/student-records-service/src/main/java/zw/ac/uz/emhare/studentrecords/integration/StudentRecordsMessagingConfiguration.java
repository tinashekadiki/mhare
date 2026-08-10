package zw.ac.uz.emhare.studentrecords.integration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
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
public class StudentRecordsMessagingConfiguration {

    @Bean
    Declarables studentRecordsMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(eventsExchange);
        declarables.add(deadLetterExchange);
        addQueue(declarables, eventsExchange, deadLetterExchange,
                EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_QUEUE,
                EmhareMessagingTopology.ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT);
        addQueue(declarables, eventsExchange, deadLetterExchange,
                EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_QUEUE,
                EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT);
        addQueue(declarables, eventsExchange, deadLetterExchange,
                EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_QUEUE,
                EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT);
        return new Declarables(declarables);
    }

    private void addQueue(
            List<Declarable> declarables,
            TopicExchange eventsExchange,
            DirectExchange deadLetterExchange,
            String queueName,
            String eventType) {
        String deadQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
        Binding eventBinding = BindingBuilder.bind(queue).to(eventsExchange).with(eventType);
        Binding deadBinding = BindingBuilder.bind(deadQueue).to(deadLetterExchange)
                .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName));
        declarables.add(queue);
        declarables.add(deadQueue);
        declarables.add(eventBinding);
        declarables.add(deadBinding);
    }
}
