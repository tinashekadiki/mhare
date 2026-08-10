package zw.ac.uz.emhare.admissions.integration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
public class AdmissionsMessagingConfiguration {

    @Bean
    Declarables admissionsPaymentReferenceMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);

        String queueName = EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_QUEUE;
        String deadLetterQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName))
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        Binding eventBinding = BindingBuilder.bind(queue)
                .to(eventsExchange)
                .with(EmhareMessagingTopology.PAYMENT_REFERENCE_UPDATED_EVENT);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName));
        return new Declarables(
                eventsExchange,
                deadLetterExchange,
                queue,
                deadLetterQueue,
                eventBinding,
                deadLetterBinding);
    }

    @Bean
    Declarables admissionsStudentConversionMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_QUEUE;
        String deadLetterQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        return new Declarables(
                eventsExchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(eventsExchange)
                        .with(EmhareMessagingTopology.STUDENT_CONVERSION_COMPLETED_EVENT),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }

    @Bean
    Declarables admissionsDocumentVerificationMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_ADMISSIONS_QUEUE;
        String deadLetterQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        return new Declarables(
                eventsExchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(eventsExchange)
                        .with(EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }

    @Bean
    Declarables admissionsOfferLetterStoredMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.OFFER_LETTER_STORED_ADMISSIONS_QUEUE;
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadLetterQueue = QueueBuilder.durable(EmhareMessagingTopology.deadLetterQueue(queueName)).build();
        return new Declarables(eventsExchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(eventsExchange).with(EmhareMessagingTopology.OFFER_LETTER_STORED_EVENT),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }
}
