package zw.ac.uz.emhare.notifications;

import zw.ac.uz.emhare.notifications.domain.model.NotificationConsent;
import zw.ac.uz.emhare.notifications.domain.model.NotificationEventInbox;
import zw.ac.uz.emhare.notifications.domain.model.NotificationProviderCallback;
import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;
import zw.ac.uz.emhare.notifications.domain.model.NotificationTemplate;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.notifications.api.model.NotificationApiModels.*;

/** @author Tinashe K */
@Testcontainers
@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/test-jwks","spring.rabbitmq.listener.simple.auto-startup=false","emhare.notifications.dispatch-interval-ms=3600000","emhare.notifications.provider=local-log"})
class NotificationLifecycleIntegrationTest {
    private static final UUID MAKER=UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID CHECKER=UUID.fromString("50000000-0000-4000-8000-000000000002");
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("emhare_notifications_lifecycle").withUsername("emhare_service").withPassword("emhare_test_password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword);}
    @Autowired NotificationService service;
    @Autowired NotificationInboxProcessor inboxProcessor;
    @Autowired ObjectMapper objectMapper;

    @Test void manualRetryPreservesMonotonicAttemptNumbers(){
        NotificationTemplate template=new NotificationTemplate("TEST",1,"Test","TEST",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.TRANSACTIONAL,"en-ZW",null,"Body",MAKER);
        NotificationRequest request=new NotificationRequest("NTF-TEST","test-key","test-service",null,"TEST",template,null,"person","person@example.test",null,"Body",NotificationRequest.Priority.NORMAL,"NOT_REQUIRED",Instant.now(),1,false);
        request.startAttempt();request.deliveryFailed("TEST","TIMEOUT","Timed out",false,Instant.now(),null);request.retryNow(CHECKER,"Operator authorised another delivery attempt.",Instant.now(),0);request.startAttempt();
        assertEquals(2,request.getAttemptCount());assertEquals(6,request.getMaxAttempts());assertEquals(CHECKER,request.getManualRetryByUserId());
    }

    @Test void governsTemplatesConsentIdempotencyDeliveryAndImmutableEvidence(){
        TemplateSummary transactional=service.createTemplate(new CreateTemplate("APPLICATION_SUBMITTED",1,"Application submitted","APPLICATION_SUBMITTED",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.TRANSACTIONAL,"en-ZW","Application {{applicationNumber}} received","Dear {{name}}, application {{applicationNumber}} was received."),MAKER);
        TemplateSummary preparedTransactional=transactional;
        assertThrows(IllegalStateException.class,()->service.transitionTemplate(preparedTransactional.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"self approval",preparedTransactional.version()),MAKER));
        transactional=service.transitionTemplate(transactional.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"Template wording and recipient data controls verified",transactional.version()),CHECKER);
        QueueNotification command=new QueueNotification("admissions:application:1001:submitted","admissions-service",UUID.randomUUID(),"APPLICATION_SUBMITTED",transactional.code(),NotificationTemplate.Channel.EMAIL,"en-ZW",UUID.randomUUID(),"applicant@example.test","applicant@example.test",NotificationRequest.Priority.HIGH,Instant.now(),3,Map.of("applicationNumber","APP-1001","name","Example Applicant"));
        RequestSummary queued=service.queue(command);assertEquals(NotificationRequest.Status.QUEUED,queued.status());assertEquals(queued.id(),service.queue(command).id());
        service.dispatchDue();Register afterDispatch=service.register();RequestSummary sent=afterDispatch.requests().stream().filter(item->item.id().equals(queued.id())).findFirst().orElseThrow();assertEquals(NotificationRequest.Status.SENT,sent.status());assertEquals(1,sent.attemptCount());assertEquals(1,afterDispatch.deliveryAttempts().size());

        TemplateSummary marketing=service.createTemplate(new CreateTemplate("ALUMNI_NEWS",1,"Alumni news","ALUMNI_NEWS",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.MARKETING,"en-ZW","News","Hello {{name}}"),MAKER);
        marketing=service.transitionTemplate(marketing.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"Campaign template approved",marketing.version()),CHECKER);
        RequestSummary suppressed=service.queue(new QueueNotification("marketing:news:1001","communications-service",UUID.randomUUID(),"ALUMNI_NEWS",marketing.code(),NotificationTemplate.Channel.EMAIL,"en-ZW",null,"alumni@example.test","alumni@example.test",NotificationRequest.Priority.NORMAL,null,5,Map.of("name","Graduate")));
        assertEquals(NotificationRequest.Status.SUPPRESSED,suppressed.status());assertEquals("CONSENT_MISSING",suppressed.consentDecision());
        ConsentSummary consent=service.recordConsent(new RecordConsent(null,"alumni@example.test",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.MARKETING,NotificationConsent.Status.OPTED_IN,"SELF_SERVICE","preference-centre",null));assertEquals(NotificationConsent.Status.OPTED_IN,consent.status());
    }

    @Test void deliversRecipientOwnedInAppNotificationsAndRecordsReadEvidence(){
        TemplateSummary template=service.createTemplate(new CreateTemplate("WORKFLOW_TASK_ASSIGNED",1,"Workflow task assigned","WORKFLOW_TASK_ASSIGNED",NotificationTemplate.Channel.IN_APP,NotificationTemplate.Category.WORKFLOW,"en-ZW","Task {{taskReference}}","Task {{taskReference}} requires your review."),MAKER);
        template=service.transitionTemplate(template.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"Workflow template independently reviewed.",template.version()),CHECKER);
        UUID recipientUserId=UUID.randomUUID();
        RequestSummary queued=service.queue(new QueueNotification("workflow:task:in-app:1001","admissions-service",UUID.randomUUID(),"WORKFLOW_TASK_ASSIGNED",template.code(),NotificationTemplate.Channel.IN_APP,"en-ZW",recipientUserId,recipientUserId.toString(),recipientUserId.toString(),NotificationRequest.Priority.HIGH,null,3,Map.of("taskReference","ADM-REVIEW-1001")));
        service.dispatchDue();
        RequestSummary delivered=service.register().requests().stream().filter(item->item.id().equals(queued.id())).findFirst().orElseThrow();
        assertEquals(NotificationRequest.Status.SENT,delivered.status());
        assertEquals(NotificationRequest.ProviderDeliveryStatus.DELIVERED,delivered.providerDeliveryStatus());
        assertEquals("IN_APP",delivered.providerCode());
        InAppSummary inboxItem=service.myInAppNotifications(recipientUserId).stream().filter(item->item.notificationRequestId().equals(queued.id())).findFirst().orElseThrow();
        InAppSummary read=service.markInAppRead(inboxItem.id(),recipientUserId,inboxItem.version());
        assertNotNull(read.readAt());assertEquals(recipientUserId,read.readByUserId());
        assertThrows(java.util.NoSuchElementException.class,()->service.markInAppRead(inboxItem.id(),UUID.randomUUID(),read.version()));
    }

    @Test void recordsIdempotentProviderDeliveryCallbacksWithoutRewritingBusinessState(){
        TemplateSummary template=service.createTemplate(new CreateTemplate("PAYMENT_CONFIRMED",1,"Payment confirmed","PAYMENT_CONFIRMED",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.TRANSACTIONAL,"en-ZW","Payment {{receipt}} confirmed","Receipt {{receipt}} has been posted."),MAKER);
        template=service.transitionTemplate(template.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"Finance notification wording independently reviewed.",template.version()),CHECKER);
        RequestSummary queued=service.queue(new QueueNotification("finance:payment:confirmed:1001","finance-service",UUID.randomUUID(),"PAYMENT_CONFIRMED",template.code(),NotificationTemplate.Channel.EMAIL,"en-ZW",UUID.randomUUID(),"student@example.test","student@example.test",NotificationRequest.Priority.NORMAL,null,3,Map.of("receipt","RCT-1001")));
        service.dispatchDue();
        RequestSummary accepted=service.register().requests().stream().filter(item->item.id().equals(queued.id())).findFirst().orElseThrow();
        assertEquals(NotificationRequest.ProviderDeliveryStatus.ACCEPTED,accepted.providerDeliveryStatus());
        ProviderCallbackPayload payload=new ProviderCallbackPayload("provider-event-1001",accepted.providerMessageId(),NotificationProviderCallback.DeliveryStatus.DELIVERED,Instant.now().plus(1,ChronoUnit.SECONDS),null,null);
        CallbackSummary first=service.recordProviderCallback(accepted.providerCode(),payload,Map.of("providerEventId",payload.providerEventId(),"providerMessageId",payload.providerMessageId(),"deliveryStatus","DELIVERED"));
        CallbackSummary duplicate=service.recordProviderCallback(accepted.providerCode(),payload,Map.of("providerEventId",payload.providerEventId(),"providerMessageId",payload.providerMessageId(),"deliveryStatus","DELIVERED"));
        assertEquals(first.id(),duplicate.id());
        RequestSummary delivered=service.register().requests().stream().filter(item->item.id().equals(queued.id())).findFirst().orElseThrow();
        assertEquals(NotificationRequest.Status.SENT,delivered.status());
        assertEquals(NotificationRequest.ProviderDeliveryStatus.DELIVERED,delivered.providerDeliveryStatus());
    }

    @Test void processesDurableNotificationIntentInboxIdempotently() throws Exception {
        TemplateSummary template=service.createTemplate(new CreateTemplate("MISSING_DOCUMENTS",1,"Missing documents","MISSING_DOCUMENTS",NotificationTemplate.Channel.EMAIL,NotificationTemplate.Category.WORKFLOW,"en-ZW","Documents required for {{applicationNumber}}","Upload {{documentList}} for {{applicationNumber}}."),MAKER);
        template=service.transitionTemplate(template.id(),new TemplateTransition(NotificationTemplate.Status.ACTIVE,"Admissions workflow template independently reviewed.",template.version()),CHECKER);
        UUID eventId=UUID.randomUUID();UUID sourceEventId=UUID.randomUUID();
        NotificationRequestedEvent event=new NotificationRequestedEvent(eventId,NotificationRequestedEvent.CURRENT_SCHEMA_VERSION,Instant.now(),"admissions-service",sourceEventId,"admissions:missing-documents:"+sourceEventId,"MISSING_DOCUMENTS",template.code(),"EMAIL","en-ZW",UUID.randomUUID(),"applicant@example.test","applicant@example.test","HIGH",null,5,Map.of("applicationNumber","APP-1001","documentList","national ID and transcript"));
        String payload=objectMapper.writeValueAsString(event);
        service.captureEvent("admissions-service",eventId,EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,payload);
        service.captureEvent("admissions-service",eventId,EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,payload);
        inboxProcessor.processDue();
        Register register=service.register();
        InboxSummary inbox=register.eventInbox().stream().filter(item->item.sourceEventId().equals(eventId)).findFirst().orElseThrow();
        assertEquals(NotificationEventInbox.Status.PROCESSED,inbox.status());
        assertEquals(1,inbox.attemptCount());
        assertEquals(1,register.requests().stream().filter(item->item.idempotencyKey().equals(event.idempotencyKey())).count());
    }
}
