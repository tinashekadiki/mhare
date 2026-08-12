package zw.ac.uz.emhare.documentsreporting.integration;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionResultProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedResultProjectionRepository;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.common.messaging.PublishedResultVersionCreatedEvent;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.ProgressionDecisionResultProjection;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedResultProjection;

/** @author Tinashe K */
@Component
public class OfficialResultsProjectionListener {

    private static final String SOURCE_SERVICE = "assessment-results-service";

    private final DocumentsReportingIntegrationInboxRepository inboxRepository;
    private final PublishedResultProjectionRepository resultRepository;
    private final ProgressionDecisionProjectionRepository decisionRepository;
    private final ProgressionDecisionResultProjectionRepository decisionResultRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OfficialResultsProjectionListener(
            DocumentsReportingIntegrationInboxRepository inboxRepository,
            PublishedResultProjectionRepository resultRepository,
            ProgressionDecisionProjectionRepository decisionRepository,
            ProgressionDecisionResultProjectionRepository decisionResultRepository,
            GeneratedDocumentRepository documentRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inboxRepository = inboxRepository;
        this.resultRepository = resultRepository;
        this.decisionRepository = decisionRepository;
        this.decisionResultRepository = decisionResultRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_DOCUMENTS_QUEUE)
    @Transactional
    public void onPublishedResult(Message message) {
        String payload = payload(message);
        PublishedResultVersionCreatedEvent event = deserialize(payload, PublishedResultVersionCreatedEvent.class);
        validateEnvelope(message, event.eventId(), event.schemaVersion());
        DocumentsReportingIntegrationInbox inbox = beginInbox(
                event.eventId(), EmhareMessagingTopology.PUBLISHED_RESULT_VERSION_CREATED_EVENT, payload);
        if (inbox == null) {
            return;
        }

        if (resultRepository.findBySourcePublishedResultIdAndDeletedAtIsNull(event.publishedResultId()).isPresent()) {
            throw new IllegalStateException("A published result source ID was reused by a different event.");
        }
        var currentResult = resultRepository
                .findByStudentIdAndAcademicPeriodIdAndModuleIdAndCurrentVersionTrueAndDeletedAtIsNull(
                        event.studentId(), event.academicPeriodId(), event.moduleId());
        if (event.publicationVersion() == 1 && currentResult.isPresent()) {
            throw new IllegalStateException("An initial result publication already exists for this reporting scope.");
        }
        if (event.publicationVersion() > 1) {
            PublishedResultProjection predecessor = resultRepository
                    .findBySourcePublishedResultIdAndDeletedAtIsNull(event.supersedesPublishedResultId())
                    .orElseThrow(() -> new IllegalStateException(
                            "The superseded result publication has not reached Documents and Reporting."));
            validateResultLineage(predecessor, event);
            currentResult.filter(result -> !result.getId().equals(predecessor.getId()))
                    .ifPresent(result -> {
                        throw new IllegalStateException("Result publication lineage does not match the current projection.");
                    });
            predecessor.markSuperseded();
            resultRepository.saveAndFlush(predecessor);
        }
        resultRepository.saveAndFlush(new PublishedResultProjection(event));
        inbox.markProcessed(clock.instant());
    }

    @RabbitListener(queues = EmhareMessagingTopology.PROGRESSION_DECISION_PUBLISHED_DOCUMENTS_QUEUE)
    @Transactional
    public void onProgressionDecision(Message message) {
        String payload = payload(message);
        ProgressionDecisionPublishedEvent event = deserialize(payload, ProgressionDecisionPublishedEvent.class);
        validateEnvelope(message, event.eventId(), event.schemaVersion());
        DocumentsReportingIntegrationInbox inbox = beginInbox(
                event.eventId(), EmhareMessagingTopology.PROGRESSION_DECISION_PUBLISHED_EVENT, payload);
        if (inbox == null) {
            return;
        }

        if (decisionRepository.findBySourceProgressionDecisionIdAndDeletedAtIsNull(
                event.progressionDecisionId()).isPresent()) {
            throw new IllegalStateException("A progression decision source ID was reused by a different event.");
        }
        if (event.sourcePublishedResultIds() == null || event.sourcePublishedResultIds().isEmpty()) {
            throw new IllegalStateException("A progression publication must carry exact result evidence.");
        }
        List<PublishedResultProjection> resultEvidence = new ArrayList<>();
        for (UUID sourceResultId : event.sourcePublishedResultIds()) {
            PublishedResultProjection result = resultRepository
                    .findBySourcePublishedResultIdAndDeletedAtIsNull(sourceResultId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Progression result evidence has not reached Documents and Reporting."));
            validateDecisionEvidence(result, event);
            resultEvidence.add(result);
        }
        if (resultEvidence.stream().map(PublishedResultProjection::getModuleId).distinct().count()
                != resultEvidence.size()) {
            throw new IllegalStateException("Progression evidence contains a duplicate Module.");
        }
        validateDecisionMetrics(resultEvidence, event);

        var currentDecision = decisionRepository
                .findByStudentIdAndAcademicPeriodIdAndCurrentVersionTrueAndDeletedAtIsNull(
                        event.studentId(), event.academicPeriodId());
        if (event.decisionVersion() == 1 && currentDecision.isPresent()) {
            throw new IllegalStateException("An initial progression decision already exists for this reporting scope.");
        }
        if (event.decisionVersion() > 1 && currentDecision.isEmpty()) {
            throw new IllegalStateException("The superseded progression decision has not reached Documents and Reporting.");
        }
        currentDecision.ifPresent(existingDecision -> {
                    if (!existingDecision.getSourceProgressionDecisionId().equals(event.supersedesDecisionId())
                            || existingDecision.getDecisionVersion() + 1 != event.decisionVersion()) {
                        throw new IllegalStateException("Progression decision lineage does not match the current projection.");
                    }
                    existingDecision.markSuperseded();
                    decisionRepository.saveAndFlush(existingDecision);
                });

        ProgressionDecisionProjection decision = decisionRepository.saveAndFlush(
                new ProgressionDecisionProjection(event));
        decisionResultRepository.saveAllAndFlush(resultEvidence.stream()
                .map(result -> new ProgressionDecisionResultProjection(decision, result))
                .toList());
        if (!documentRepository
                .existsByDocumentTypeAndSourceProgressionDecisionIdAndSourceProgressionDecisionVersionAndDeletedAtIsNull(
                        GeneratedDocument.DocumentType.RESULT_SLIP,
                        event.progressionDecisionId(),
                        event.decisionVersion())) {
            documentRepository.save(new GeneratedDocument(decision, clock.instant()));
        }
        inbox.markProcessed(clock.instant());
    }

    private DocumentsReportingIntegrationInbox beginInbox(UUID eventId, String eventType, String payload) {
        var existing = inboxRepository.findById(eventId);
        if (existing.isPresent()) {
            if (!existing.get().hasEnvelope(eventType, SOURCE_SERVICE)
                    || !jsonEquals(existing.get().getPayload(), payload)) {
                throw new IllegalStateException("An integration event ID was reused with different content.");
            }
            if (existing.get().getProcessedAt() != null) {
                return null;
            }
            return existing.get();
        }
        return inboxRepository.save(new DocumentsReportingIntegrationInbox(
                eventId, eventType, SOURCE_SERVICE, payload, clock.instant()));
    }

    private void validateEnvelope(Message message, UUID eventId, int schemaVersion) {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported Assessment and Results event schema version.");
        }
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || !eventId.toString().equals(messageId)) {
            throw new IllegalArgumentException("RabbitMQ message ID does not match the event ID.");
        }
        Object sourceService = message.getMessageProperties().getHeader("source-service");
        if (!SOURCE_SERVICE.equals(sourceService)) {
            throw new IllegalArgumentException("Official result events must originate from Assessment and Results.");
        }
    }

    private void validateResultLineage(
            PublishedResultProjection predecessor,
            PublishedResultVersionCreatedEvent event) {
        if (!predecessor.getStudentId().equals(event.studentId())
                || !predecessor.getAcademicPeriodId().equals(event.academicPeriodId())
                || !predecessor.getModuleId().equals(event.moduleId())
                || predecessor.getPublicationVersion() + 1 != event.publicationVersion()) {
            throw new IllegalStateException("Published result correction lineage is invalid.");
        }
    }

    private void validateDecisionEvidence(
            PublishedResultProjection result,
            ProgressionDecisionPublishedEvent event) {
        if (!result.getStudentId().equals(event.studentId())
                || !result.getAcademicPeriodId().equals(event.academicPeriodId())
                || !result.getProgrammeId().equals(event.programmeId())
                || !result.getProgrammeVersionId().equals(event.programmeVersionId())) {
            throw new IllegalStateException("Progression result evidence is outside the published decision scope.");
        }
    }

    private void validateDecisionMetrics(
            List<PublishedResultProjection> results,
            ProgressionDecisionPublishedEvent event) {
        BigDecimal attemptedCredits = BigDecimal.ZERO;
        BigDecimal passedCredits = BigDecimal.ZERO;
        BigDecimal failedCredits = BigDecimal.ZERO;
        BigDecimal weightedMarks = BigDecimal.ZERO;
        int failedModules = 0;
        int failedCompulsoryModules = 0;
        for (PublishedResultProjection result : results) {
            attemptedCredits = attemptedCredits.add(result.getCreditValue());
            weightedMarks = weightedMarks.add(result.getFinalMark().multiply(result.getCreditValue()));
            if (result.isPassing()) {
                passedCredits = passedCredits.add(result.getCreditValue());
            } else {
                failedCredits = failedCredits.add(result.getCreditValue());
                failedModules++;
                if ("COMPULSORY".equals(result.getCurriculumModuleType())) {
                    failedCompulsoryModules++;
                }
            }
        }
        BigDecimal weightedAverage = weightedMarks.divide(attemptedCredits, 2, RoundingMode.HALF_UP);
        if (attemptedCredits.compareTo(event.attemptedCredits()) != 0
                || passedCredits.compareTo(event.passedCredits()) != 0
                || failedCredits.compareTo(event.failedCredits()) != 0
                || failedModules != event.failedModules()
                || failedCompulsoryModules != event.failedCompulsoryModules()
                || weightedAverage.compareTo(event.weightedAverage()) != 0) {
            throw new IllegalStateException(
                    "Progression metrics do not reconcile to the exact published result evidence.");
        }
    }

    private String payload(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    private <T> T deserialize(String payload, Class<T> eventType) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Official result event could not be deserialized.", exception);
        }
    }

    private boolean jsonEquals(String firstPayload, String secondPayload) {
        try {
            return objectMapper.readTree(firstPayload).equals(objectMapper.readTree(secondPayload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored integration inbox payload is invalid JSON.", exception);
        }
    }
}
