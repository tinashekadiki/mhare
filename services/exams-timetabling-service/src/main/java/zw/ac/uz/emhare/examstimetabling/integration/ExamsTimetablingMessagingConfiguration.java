package zw.ac.uz.emhare.examstimetabling.integration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** @author Tinashe K */
@Configuration
public class ExamsTimetablingMessagingConfiguration {
    @Bean
    Declarables examsTimetablingMessagingTopology() {
        TopicExchange events = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EXAMS_QUEUE;
        String deadQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
        Binding eventBinding = BindingBuilder.bind(queue).to(events)
                .with(EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT);
        Binding deadBinding = BindingBuilder.bind(deadQueue).to(deadLetters)
                .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName));
        return new Declarables(events, deadLetters, queue, deadQueue, eventBinding, deadBinding);
    }
}
