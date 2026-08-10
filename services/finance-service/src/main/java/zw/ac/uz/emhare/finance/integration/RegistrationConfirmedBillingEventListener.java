package zw.ac.uz.emhare.finance.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.finance.billing.GovernedFinanceBillingService;

/** Converts authoritative confirmed registrations through Finance-owned billing policies. @author Tinashe K */
@Component
public class RegistrationConfirmedBillingEventListener {
    static final UUID REGISTRATION_BILLING_INTEGRATION_ACTOR=UUID.nameUUIDFromBytes("finance-registration-billing-integration".getBytes(StandardCharsets.UTF_8));
    private final FinanceIntegrationInbox inbox;private final GovernedFinanceBillingService billingService;private final ObjectMapper objectMapper;private final Clock clock;
    public RegistrationConfirmedBillingEventListener(FinanceIntegrationInbox inbox,GovernedFinanceBillingService billingService,ObjectMapper objectMapper,Clock clock){this.inbox=inbox;this.billingService=billingService;this.objectMapper=objectMapper;this.clock=clock;}
    @RabbitListener(queues=EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_FINANCE_QUEUE)
    @Transactional
    public void receive(Message message){String payload=new String(message.getBody(),StandardCharsets.UTF_8);StudentRegistrationConfirmedEvent event=deserialize(payload);if(!inbox.claim(event.eventId(),EmhareMessagingTopology.STUDENT_REGISTRATION_CONFIRMED_EVENT,"student-records-service",payload,clock.instant()))return;billingService.importConfirmedRegistration(event,REGISTRATION_BILLING_INTEGRATION_ACTOR);inbox.markProcessed(event.eventId(),clock.instant());}
    private StudentRegistrationConfirmedEvent deserialize(String payload){try{return objectMapper.readValue(payload,StudentRegistrationConfirmedEvent.class);}catch(JacksonException exception){throw new IllegalArgumentException("Confirmed-registration billing payload is invalid.",exception);}}
}
