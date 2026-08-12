package zw.ac.uz.emhare.documentsreporting.integration;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.ProgressionDecisionResultProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedResultProjectionRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.ProgressionDecisionPublishedEvent;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedResultProjection;

/** @author Tinashe K */
class OfficialResultsProjectionListenerTest {

    @Test
    void rejectsProgressionMetricsThatDoNotReconcileToPublishedEvidence() throws Exception {
        var inboxRepository = mock(DocumentsReportingIntegrationInboxRepository.class);
        var resultRepository = mock(PublishedResultProjectionRepository.class);
        var decisionRepository = mock(ProgressionDecisionProjectionRepository.class);
        var decisionResultRepository = mock(ProgressionDecisionResultProjectionRepository.class);
        var documentRepository = mock(GeneratedDocumentRepository.class);
        var objectMapper = new ObjectMapper();
        Instant occurredAt = Instant.parse("2027-12-20T10:00:00Z");
        Clock clock = Clock.fixed(occurredAt, ZoneOffset.UTC);
        UUID eventId = UUID.randomUUID();
        UUID sourceResultId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID programmeVersionId = UUID.randomUUID();
        UUID academicPeriodId = UUID.randomUUID();

        ProgressionDecisionPublishedEvent event = new ProgressionDecisionPublishedEvent(
                eventId,
                1,
                occurredAt,
                UUID.randomUUID(),
                "PRG-2027-S1-STU-001-V1",
                1,
                null,
                UUID.randomUUID(),
                "BACC-P1",
                3,
                UUID.randomUUID(),
                studentId,
                "STU-001",
                UUID.randomUUID(),
                programmeId,
                programmeVersionId,
                academicPeriodId,
                "2027-S1",
                1,
                "PROCEED",
                "Proceed to programme period 2",
                2,
                new BigDecimal("12.00"),
                new BigDecimal("12.00"),
                BigDecimal.ZERO,
                0,
                0,
                new BigDecimal("99.00"),
                UUID.randomUUID(),
                occurredAt,
                List.of(sourceResultId));
        String payload = objectMapper.writeValueAsString(event);
        var message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setMessageId(eventId.toString())
                .setHeader("source-service", "assessment-results-service")
                .build();

        PublishedResultProjection result = mock(PublishedResultProjection.class);
        when(result.getStudentId()).thenReturn(studentId);
        when(result.getProgrammeId()).thenReturn(programmeId);
        when(result.getProgrammeVersionId()).thenReturn(programmeVersionId);
        when(result.getAcademicPeriodId()).thenReturn(academicPeriodId);
        when(result.getModuleId()).thenReturn(UUID.randomUUID());
        when(result.getCreditValue()).thenReturn(new BigDecimal("12.00"));
        when(result.getFinalMark()).thenReturn(new BigDecimal("72.00"));
        when(result.isPassing()).thenReturn(true);
        when(result.getCurriculumModuleType()).thenReturn("COMPULSORY");

        when(inboxRepository.findById(eventId)).thenReturn(Optional.empty());
        when(inboxRepository.save(any(DocumentsReportingIntegrationInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(decisionRepository.findBySourceProgressionDecisionIdAndDeletedAtIsNull(
                event.progressionDecisionId())).thenReturn(Optional.empty());
        when(resultRepository.findBySourcePublishedResultIdAndDeletedAtIsNull(sourceResultId))
                .thenReturn(Optional.of(result));

        var listener = new OfficialResultsProjectionListener(
                inboxRepository,
                resultRepository,
                decisionRepository,
                decisionResultRepository,
                documentRepository,
                objectMapper,
                clock);

        assertThrows(IllegalStateException.class, () -> listener.onProgressionDecision(message));
    }
}
