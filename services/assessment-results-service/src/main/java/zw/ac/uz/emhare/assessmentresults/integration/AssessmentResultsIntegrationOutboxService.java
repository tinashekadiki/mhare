package zw.ac.uz.emhare.assessmentresults.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.assessmentresults.progression.StudentOverallDecision;
import zw.ac.uz.emhare.assessmentresults.progression.StudentOverallDecisionResult;
import zw.ac.uz.emhare.assessmentresults.result.ModuleResult;
import zw.ac.uz.emhare.assessmentresults.result.PublishedResult;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.common.messaging.PublishedResultVersionCreatedEvent;

/** @author Tinashe K */
@Service
public class AssessmentResultsIntegrationOutboxService {

    private final AssessmentResultsOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssessmentResultsIntegrationOutboxService(
            AssessmentResultsOutboxEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueuePublishedResult(PublishedResult publishedResult) {
        Instant occurredAt = clock.instant();
        UUID eventId = UUID.randomUUID();
        ModuleResult moduleResult = publishedResult.getModuleResult();
        var rosterEntry = moduleResult.getRosterEntry();
        var rosterImport = rosterEntry.getRosterImport();
        save(eventId, EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_EVENT,
                new PublishedResultVersionCreatedEvent(
                        eventId,
                        PublishedResultVersionCreatedEvent.CURRENT_SCHEMA_VERSION,
                        occurredAt,
                        publishedResult.getId(),
                        publishedResult.getResultBatch().getId(),
                        moduleResult.getId(),
                        publishedResult.getStudentId(),
                        publishedResult.getStudentNumber(),
                        rosterImport.getProgrammeEnrolmentId(),
                        rosterImport.getProgrammeId(),
                        rosterImport.getProgrammeVersionId(),
                        publishedResult.getAcademicPeriodId(),
                        publishedResult.getAcademicPeriodCode(),
                        publishedResult.getModuleId(),
                        publishedResult.getModuleCode(),
                        publishedResult.getModuleName(),
                        rosterEntry.getCurriculumModuleType(),
                        rosterEntry.getCreditValue(),
                        publishedResult.getFinalMark(),
                        publishedResult.getGrade(),
                        publishedResult.getRemark(),
                        moduleResult.getResultStatus() == ModuleResult.Status.PASS,
                        publishedResult.getPublicationVersion(),
                        publishedResult.getSupersedesPublishedResultId(),
                        publishedResult.getResultAmendmentId(),
                        publishedResult.getPublishedByUserId(),
                        publishedResult.getPublishedAt()),
                occurredAt);
    }

    public void enqueueProgressionDecision(
            StudentOverallDecision decision,
            List<StudentOverallDecisionResult> evidence) {
        Instant occurredAt = clock.instant();
        UUID eventId = UUID.randomUUID();
        var ruleSet = decision.getRuleSet();
        save(eventId, EmhareMessagingTopology.PROGRESSION_DECISION_PUBLISHED_EVENT,
                new ProgressionDecisionPublishedEvent(
                        eventId,
                        ProgressionDecisionPublishedEvent.CURRENT_SCHEMA_VERSION,
                        occurredAt,
                        decision.getId(),
                        decision.getDecisionNumber(),
                        decision.getDecisionVersion(),
                        decision.getSupersedesDecisionId(),
                        ruleSet.getId(),
                        ruleSet.getRuleCode(),
                        ruleSet.getRuleVersion(),
                        decision.getRosterImport().getId(),
                        decision.getStudentId(),
                        decision.getStudentNumber(),
                        decision.getProgrammeEnrolmentId(),
                        decision.getProgrammeId(),
                        decision.getProgrammeVersionId(),
                        decision.getAcademicPeriodId(),
                        decision.getAcademicPeriodCode(),
                        decision.getProgrammePeriodNumber(),
                        decision.getDecisionCode().name(),
                        decision.getDecisionLabel(),
                        decision.getNextProgrammePeriodNumber(),
                        decision.getAttemptedCredits(),
                        decision.getPassedCredits(),
                        decision.getFailedCredits(),
                        decision.getFailedModules(),
                        decision.getFailedCompulsoryModules(),
                        decision.getWeightedAverage(),
                        decision.getPublishedByUserId(),
                        decision.getPublishedAt(),
                        evidence.stream()
                                .map(item -> item.getPublishedResult().getId())
                                .toList()),
                occurredAt);
    }

    private void save(UUID eventId, String eventType, Object event, Instant occurredAt) {
        repository.save(new AssessmentResultsOutboxEvent(
                eventId, eventType, eventType, serialize(event), occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Assessment and Results integration event could not be serialized.", exception);
        }
    }
}
