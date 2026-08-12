package zw.ac.uz.emhare.coreidentity.integration;

import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;

import zw.ac.uz.emhare.coreidentity.workflow.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.MissingApplicationDocumentWorkflowRequestedEvent;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;

/** @author Tinashe K */
class MissingApplicationDocumentWorkflowRequestedEventListenerTest {

    @Test
    void createsOneApplicantCorrectionTaskFromAClaimedEvent() throws Exception {
        CoreIdentityIntegrationInbox inbox = org.mockito.Mockito.mock(CoreIdentityIntegrationInbox.class);
        WorkflowService workflowService = org.mockito.Mockito.mock(WorkflowService.class);
        PlatformUserRepository platformUserRepository = org.mockito.Mockito.mock(PlatformUserRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant receivedAt = Instant.parse("2026-08-08T19:00:00Z");
        Clock clock = Clock.fixed(receivedAt, ZoneOffset.UTC);
        MissingApplicationDocumentWorkflowRequestedEventListener listener =
                new MissingApplicationDocumentWorkflowRequestedEventListener(
                        inbox, workflowService, platformUserRepository, objectMapper, clock);
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID applicantUserId = UUID.randomUUID();
        UUID verifierUserId = UUID.randomUUID();
        UUID localVerifierUserId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Instant dueAt = Instant.parse("2026-08-31T23:59:59Z");
        MissingApplicationDocumentWorkflowRequestedEvent event =
                new MissingApplicationDocumentWorkflowRequestedEvent(
                        eventId,
                        MissingApplicationDocumentWorkflowRequestedEvent.CURRENT_SCHEMA_VERSION,
                        receivedAt,
                        applicationId,
                        "EMH-2026-0042",
                        applicantUserId,
                        documentId,
                        3,
                        "NATIONAL_ID",
                        "The identity number is not readable.",
                        verifierUserId,
                        dueAt);
        String payload = objectMapper.writeValueAsString(event);
        when(inbox.claim(
                eventId,
                EmhareMessagingTopology.MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT,
                "admissions-service",
                payload,
                receivedAt)).thenReturn(true);
        PlatformUser verifier = org.mockito.Mockito.mock(PlatformUser.class);
        when(platformUserRepository.findById(verifierUserId)).thenReturn(Optional.empty());
        when(platformUserRepository.findByKeycloakUserId(verifierUserId)).thenReturn(Optional.of(verifier));
        when(verifier.getId()).thenReturn(localVerifierUserId);

        listener.receive(new Message(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ArgumentCaptor<CreateWorkflowCommand> commandCaptor = ArgumentCaptor.forClass(CreateWorkflowCommand.class);
        verify(workflowService).createWorkflow(commandCaptor.capture(), eq(localVerifierUserId));
        CreateWorkflowCommand command = commandCaptor.getValue();
        assertEquals("APPLICATION_DOCUMENT_CORRECTION", command.workflowCode());
        assertEquals(applicationId, command.subjectId());
        assertEquals("EMH-2026-0042", command.subjectReference());
        assertEquals(applicantUserId, command.assignedUserId());
        assertEquals(WorkflowScopeType.INSTITUTION, command.scopeType());
        assertEquals(dueAt, command.dueAt());
        verify(inbox).markProcessed(eventId, receivedAt);
    }

    @Test
    void duplicateEventDoesNotCreateAnotherWorkflow() throws Exception {
        CoreIdentityIntegrationInbox inbox = org.mockito.Mockito.mock(CoreIdentityIntegrationInbox.class);
        WorkflowService workflowService = org.mockito.Mockito.mock(WorkflowService.class);
        PlatformUserRepository platformUserRepository = org.mockito.Mockito.mock(PlatformUserRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant receivedAt = Instant.parse("2026-08-08T19:00:00Z");
        UUID eventId = UUID.randomUUID();
        MissingApplicationDocumentWorkflowRequestedEvent event =
                new MissingApplicationDocumentWorkflowRequestedEvent(
                        eventId, 1, receivedAt, UUID.randomUUID(), "EMH-2026-0042",
                        UUID.randomUUID(), UUID.randomUUID(), 3, "NATIONAL_ID",
                        "The identity number is not readable.", UUID.randomUUID(), null);
        String payload = objectMapper.writeValueAsString(event);
        when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(false);
        MissingApplicationDocumentWorkflowRequestedEventListener listener =
                new MissingApplicationDocumentWorkflowRequestedEventListener(
                        inbox,
                        workflowService,
                        platformUserRepository,
                        objectMapper,
                        Clock.fixed(receivedAt, ZoneOffset.UTC));

        listener.receive(new Message(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        verify(workflowService, never()).createWorkflow(any(), any());
        verify(platformUserRepository, never()).findById(any());
    }
}
