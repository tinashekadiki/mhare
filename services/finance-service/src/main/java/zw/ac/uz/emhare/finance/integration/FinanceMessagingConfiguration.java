package zw.ac.uz.emhare.finance.integration;

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
public class FinanceMessagingConfiguration {

    @Bean
    Declarables financeApplicationFeeMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);

        String queueName = EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_QUEUE;
        String deadLetterQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName))
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        Binding eventBinding = BindingBuilder.bind(queue)
                .to(eventsExchange)
                .with(EmhareMessagingTopology.APPLICATION_FEE_REQUIRED_EVENT);
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
    Declarables financeStudentAccountMessagingTopology() {
        TopicExchange eventsExchange = new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                EmhareMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
        String queueName = EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_QUEUE;
        String deadLetterQueueName = EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        return new Declarables(
                eventsExchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(eventsExchange)
                        .with(EmhareMessagingTopology.STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                        .with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }

    @Bean
    Declarables financeRegistrationBillingMessagingTopology() {
        TopicExchange eventsExchange=new TopicExchange(EmhareMessagingTopology.EVENTS_EXCHANGE,true,false);
        DirectExchange deadLetterExchange=new DirectExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE,true,false);
        String queueName=EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_FINANCE_QUEUE;
        String deadLetterQueueName=EmhareMessagingTopology.deadLetterQueue(queueName);
        Queue queue=QueueBuilder.durable(queueName).deadLetterExchange(EmhareMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EmhareMessagingTopology.deadLetterRoutingKey(queueName)).build();
        Queue deadLetterQueue=QueueBuilder.durable(deadLetterQueueName).build();
        return new Declarables(eventsExchange,deadLetterExchange,queue,deadLetterQueue,
                BindingBuilder.bind(queue).to(eventsExchange).with(EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(EmhareMessagingTopology.deadLetterRoutingKey(queueName)));
    }
}
