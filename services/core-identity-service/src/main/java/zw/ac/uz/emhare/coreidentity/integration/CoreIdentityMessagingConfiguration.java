package zw.ac.uz.emhare.coreidentity.integration;

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
public class CoreIdentityMessagingConfiguration {
    @Bean
    Declarables coreIdentityMessagingTopology() {
        TopicExchange eventsExchange =
                new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange =
                new DirectExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName =
                EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_QUEUE;
        String deadQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName))
                .build();
        Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
        String missingDocumentQueueName =
                EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_CORE_QUEUE;
        String missingDocumentDeadQueueName = EmhareMessagingTopology.deadLetterQueue(missingDocumentQueueName);
        Queue missingDocumentQueue = QueueBuilder.durable(missingDocumentQueueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(missingDocumentQueueName))
                .build();
        Queue missingDocumentDeadQueue = QueueBuilder.durable(missingDocumentDeadQueueName).build();
        String academicReleaseQueueName = EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_CORE_QUEUE;
        Queue academicReleaseQueue = QueueBuilder.durable(academicReleaseQueueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(academicReleaseQueueName)).build();
        Queue academicReleaseDeadQueue = QueueBuilder.durable(
                EmhareMessagingTopology.deadLetterQueue(academicReleaseQueueName)).build();
        String recommendationQueueName = EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_CORE_QUEUE;
        Queue recommendationQueue = QueueBuilder.durable(recommendationQueueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(recommendationQueueName)).build();
        Queue recommendationDeadQueue = QueueBuilder.durable(
                EmhareMessagingTopology.deadLetterQueue(recommendationQueueName)).build();
        return new Declarables(
                eventsExchange,
                deadLetterExchange,
                queue,
                deadQueue,
                BindingBuilder.bind(queue)
                        .to(eventsExchange)
                        .with(EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT),
                BindingBuilder.bind(deadQueue)
                        .to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)),
                missingDocumentQueue,
                missingDocumentDeadQueue,
                BindingBuilder.bind(missingDocumentQueue)
                        .to(eventsExchange)
                        .with(EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT),
                BindingBuilder.bind(missingDocumentDeadQueue)
                        .to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(missingDocumentQueueName)),
                academicReleaseQueue, academicReleaseDeadQueue,
                BindingBuilder.bind(academicReleaseQueue).to(eventsExchange)
                        .with(EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_EVENT),
                BindingBuilder.bind(academicReleaseDeadQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(academicReleaseQueueName)),
                recommendationQueue, recommendationDeadQueue,
                BindingBuilder.bind(recommendationQueue).to(eventsExchange)
                        .with(EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_EVENT),
                BindingBuilder.bind(recommendationDeadQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(recommendationQueueName)));
    }
}
