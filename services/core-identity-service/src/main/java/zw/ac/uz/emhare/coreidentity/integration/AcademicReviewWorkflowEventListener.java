package zw.ac.uz.emhare.coreidentity.integration;

import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;

import zw.ac.uz.emhare.coreidentity.workflow.application.command.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.AcademicRecommendationRecordedEvent;
import zw.ac.uz.emhare.common.messaging.AcademicReviewReleasedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;

/** Keeps the Core task projection aligned with Admissions-owned academic reviews. @author Tinashe K */
@Component
public class AcademicReviewWorkflowEventListener {
    private final CoreIdentityIntegrationInbox inbox;
    private final WorkflowService workflowService;
    private final RoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AcademicReviewWorkflowEventListener(CoreIdentityIntegrationInbox inbox, WorkflowService workflowService,
            RoleRepository roleRepository, PlatformUserRepository userRepository, ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox;
        this.workflowService = workflowService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_CORE_QUEUE)
    @Transactional
    public void receiveRelease(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        AcademicReviewReleasedEvent event = read(payload, AcademicReviewReleasedEvent.class);
        if (event.schemaVersion() != AcademicReviewReleasedEvent.CURRENT_SCHEMA_VERSION
                || event.assignmentId() == null || event.recommendationAcademicUnitId() == null) {
            throw new IllegalArgumentException("Academic review release event is invalid or unsupported.");
        }
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_EVENT,
                "admissions-service", payload, clock.instant())) return;
        var role = roleRepository.findByCode("ACADEMIC_UNIT_STAFF")
                .orElseThrow(() -> new IllegalStateException("Academic Unit Staff role is not configured."));
        workflowService.createWorkflow(new CreateWorkflowCommand(
                "ADMISSIONS_ACADEMIC_RECOMMENDATION", "ACADEMIC_REVIEW_ASSIGNMENT", event.assignmentId(),
                event.applicationNumber() + "/" + event.programmeCode(), "Academic-unit recommendation",
                "Recommend " + event.programmeName(),
                "Review the consolidated application and record an advisory recommendation for "
                        + event.recommendationAcademicUnitName() + ".",
                null, role.getId(), WorkflowScopeType.ACADEMIC_UNIT,
                event.recommendationAcademicUnitId(), event.dueAt()), requireUser(event.releasedByUserId()));
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    @RabbitListener(queues = EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_CORE_QUEUE)
    @Transactional
    public void receiveRecommendation(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        AcademicRecommendationRecordedEvent event = read(payload, AcademicRecommendationRecordedEvent.class);
        if (event.schemaVersion() != AcademicRecommendationRecordedEvent.CURRENT_SCHEMA_VERSION
                || event.assignmentId() == null || event.recommendedByUserId() == null) {
            throw new IllegalArgumentException("Academic recommendation event is invalid or unsupported.");
        }
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.ACADEMIC_RECOMMENDATION_RECORDED_EVENT,
                "admissions-service", payload, clock.instant())) return;
        workflowService.completeSubjectWorkflow("ADMISSIONS_ACADEMIC_RECOMMENDATION", event.assignmentId(),
                event.recommendation(), "Academic-unit recommendation recorded.", requireUser(event.recommendedByUserId()));
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private java.util.UUID requireUser(java.util.UUID reference) {
        return userRepository.findById(reference).or(() -> userRepository.findByKeycloakUserId(reference))
                .filter(user -> !user.isDeleted()).map(user -> user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow actor has not been synchronized with Core Identity."));
    }
    private <T> T read(String payload, Class<T> type) {
        try { return objectMapper.readValue(payload, type); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Academic workflow event is invalid.", exception); }
    }
}
