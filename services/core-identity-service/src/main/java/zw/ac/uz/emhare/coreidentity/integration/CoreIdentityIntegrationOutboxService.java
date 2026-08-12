package zw.ac.uz.emhare.coreidentity.integration;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.CoreIdentityOutboxEvent;
import zw.ac.uz.emhare.coreidentity.infrastructure.persistence.messaging.CoreIdentityOutboxEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.NotificationRequestedEvent;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisionedEvent;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowTask;

/** @author Tinashe K */
@Service
public class CoreIdentityIntegrationOutboxService {
    private final CoreIdentityOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CoreIdentityIntegrationOutboxService(
            CoreIdentityOutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueueStudentPortalAccessProvisioned(StudentPortalAccessProvisioning provisioning) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = clock.instant();
        StudentPortalAccessProvisionedEvent event = new StudentPortalAccessProvisionedEvent(
                eventId,
                StudentPortalAccessProvisionedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                provisioning.getConversionRequestId(),
                provisioning.getStudentId(),
                provisioning.getUser().getId(),
                true,
                null);
        repository.save(new CoreIdentityOutboxEvent(
                eventId,
                EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueWorkflowTaskNotifications(WorkflowTask task, List<PlatformUser> recipients) {
        Map<String, String> variables = Map.of(
                "taskReference", task.getTaskReference(),
                "taskTitle", task.getTitle(),
                "dueAt", task.getDueAt() == null ? "No fixed due date" : task.getDueAt().toString());
        recipients.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .distinct()
                .forEach(user -> {
                    enqueueWorkflowTaskNotification(task, user, "EMAIL", user.getEmail(), variables);
                    enqueueWorkflowTaskNotification(task, user, "IN_APP", user.getId().toString(), variables);
                });
    }

    private void enqueueWorkflowTaskNotification(
            WorkflowTask task,
            PlatformUser recipient,
            String channel,
            String recipientAddress,
            Map<String, String> variables) {
        String idempotencyKey = "core:workflow-task:" + task.getId() + ":" + recipient.getId()
                + ":" + channel.toLowerCase();
        UUID eventId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        NotificationRequestedEvent notification = new NotificationRequestedEvent(
                eventId,
                NotificationRequestedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                "core-identity-service",
                eventId,
                idempotencyKey,
                "WORKFLOW_TASK",
                "WORKFLOW_TASK_" + channel,
                channel,
                "en-ZW",
                recipient.getId(),
                recipient.getId().toString(),
                recipientAddress,
                "HIGH",
                task.getDueAt(),
                8,
                variables);
        repository.save(new CoreIdentityOutboxEvent(
                eventId,
                EmhareMessagingTopology.NOTIFICATION_REQUESTED_EVENT,
                serialize(notification),
                occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Core Identity integration event could not be serialized.", exception);
        }
    }
}
