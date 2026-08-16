package zw.ac.uz.emhare.documentsreporting.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.OfferPublicationEvent;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedOfferLetterProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedOfferLetterProjection;

/** @author Tinashe K */
class OfferPublicationEventListenerTest {

    @Test
    void flushesTheSupersededPublicationBeforeInsertingItsReplacement() throws Exception {
        Instant publishedAt = Instant.parse("2026-08-16T19:12:24Z");
        UUID eventId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID generatedDocumentId = UUID.randomUUID();
        OfferPublicationEvent event = new OfferPublicationEvent(
                eventId, OfferPublicationEvent.CURRENT_SCHEMA_VERSION, publishedAt,
                UUID.randomUUID(), offerId, "SENT", generatedDocumentId, 11,
                "OFR-AUG-2026-00000001", UUID.randomUUID(), "EMH-AUG-2026-00000061",
                UUID.randomUUID(), "Wesley Oneill", UUID.randomUUID(), UUID.randomUUID(),
                "HCS", "Computer Science", publishedAt, true, null);

        DocumentsReportingIntegrationInboxRepository inboxRepository =
                mock(DocumentsReportingIntegrationInboxRepository.class);
        PublishedOfferLetterProjectionRepository projectionRepository =
                mock(PublishedOfferLetterProjectionRepository.class);
        GeneratedDocumentRepository documentRepository = mock(GeneratedDocumentRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DocumentsReportingIntegrationInbox inbox = mock(DocumentsReportingIntegrationInbox.class);
        PublishedOfferLetterProjection currentPublication = mock(PublishedOfferLetterProjection.class);
        GeneratedDocument currentDocument = mock(GeneratedDocument.class);
        GeneratedDocument replacementDocument = mock(GeneratedDocument.class);

        when(objectMapper.readValue(anyString(), eq(OfferPublicationEvent.class))).thenReturn(event);
        when(inboxRepository.findById(eventId)).thenReturn(Optional.empty());
        when(inboxRepository.save(any(DocumentsReportingIntegrationInbox.class))).thenReturn(inbox);
        when(projectionRepository.findBySourceEventId(eventId)).thenReturn(Optional.empty());
        when(projectionRepository.findByOfferIdAndCurrentPublicationTrue(offerId))
                .thenReturn(Optional.of(currentPublication));
        when(currentPublication.getGeneratedDocument()).thenReturn(currentDocument);
        when(currentDocument.getId()).thenReturn(UUID.randomUUID());
        when(documentRepository.findByIdAndDeletedAtIsNull(generatedDocumentId))
                .thenReturn(Optional.of(replacementDocument));

        OfferPublicationEventListener listener = new OfferPublicationEventListener(
                inboxRepository, projectionRepository, documentRepository, objectMapper,
                Clock.fixed(publishedAt.plusSeconds(1), ZoneOffset.UTC));

        listener.receive(new Message("{}".getBytes(StandardCharsets.UTF_8)));

        InOrder publicationReplacementOrder = inOrder(currentPublication, projectionRepository);
        publicationReplacementOrder.verify(currentPublication).supersede(publishedAt);
        publicationReplacementOrder.verify(projectionRepository).flush();
        publicationReplacementOrder.verify(projectionRepository).save(any(PublishedOfferLetterProjection.class));
    }
}
