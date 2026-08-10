package zw.ac.uz.emhare.coreidentity.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.MissingApplicationDocumentWorkflowRequestedEvent;
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.workflow.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowScopeType;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;

/** @author Tinashe K */
@Component
public class MissingApplicationDocumentWorkflowRequestedEventListener {

    private final CoreIdentityIntegrationInbox inbox;
    private final WorkflowService workflowService;
    private final PlatformUserRepository platformUserRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MissingApplicationDocumentWorkflowRequestedEventListener(
            CoreIdentityIntegrationInbox inbox,
            WorkflowService workflowService,
            PlatformUserRepository platformUserRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inbox = inbox;
        this.workflowService = workflowService;
        this.platformUserRepository = platformUserRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(
            queues = EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_CORE_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        MissingApplicationDocumentWorkflowRequestedEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(
                event.eventId(),
                EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT,
                "admissions-service",
                payload,
                clock.instant())) {
            return;
        }
        workflowService.createWorkflow(new CreateWorkflowCommand(
                "APPLICATION_DOCUMENT_CORRECTION",
                "APPLICATION",
                event.applicationId(),
                event.applicationNumber(),
                "Correct rejected application evidence",
                "Replace " + event.requirementCode().replace('_', ' '),
                event.rejectionReason() + " Document reference: " + event.documentId(),
                event.applicantUserId(),
                null,
                WorkflowScopeType.INSTITUTION,
                null,
                event.dueAt()), resolveWorkflowActorUserId(event.initiatedByUserId()));
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private UUID resolveWorkflowActorUserId(UUID actorReference) {
        return platformUserRepository.findById(actorReference)
                .or(() -> platformUserRepository.findByKeycloakUserId(actorReference))
                .filter(actor -> !actor.isDeleted())
                .map(PlatformUser::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The document reviewer has not been synchronized with Core Identity."));
    }

    private MissingApplicationDocumentWorkflowRequestedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, MissingApplicationDocumentWorkflowRequestedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Missing-document workflow event is invalid.", exception);
        }
    }

    private void validate(MissingApplicationDocumentWorkflowRequestedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion()
                        != MissingApplicationDocumentWorkflowRequestedEvent.CURRENT_SCHEMA_VERSION
                || event.applicationId() == null
                || event.applicationNumber() == null
                || event.applicationNumber().isBlank()
                || event.applicantUserId() == null
                || event.documentId() == null
                || event.documentVersion() < 0
                || event.requirementCode() == null
                || event.requirementCode().isBlank()
                || event.rejectionReason() == null
                || event.rejectionReason().isBlank()
                || event.initiatedByUserId() == null) {
            throw new IllegalArgumentException("Missing-document workflow event contract is invalid or unsupported.");
        }
    }
}
