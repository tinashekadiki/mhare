package zw.ac.uz.emhare.documentsreporting.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;
import zw.ac.uz.emhare.documentsreporting.document.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.document.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjectionRepository;

/** @author Tinashe K */
class OfferLetterRequestedEventListenerTest {

    @Test
    void duplicateOfferLetterRequestCreatesOneGeneratedDocument() throws Exception {
        DocumentsReportingIntegrationInboxRepository inboxRepository =
                mock(DocumentsReportingIntegrationInboxRepository.class);
        OfferLetterProjectionRepository projectionRepository = mock(OfferLetterProjectionRepository.class);
        GeneratedDocumentRepository documentRepository = mock(GeneratedDocumentRepository.class);
        DocumentsReportingIntegrationInbox claimedInbox = mock(DocumentsReportingIntegrationInbox.class);
        DocumentsReportingIntegrationInbox processedInbox = mock(DocumentsReportingIntegrationInbox.class);
        OfferLetterProjection projection = mock(OfferLetterProjection.class);
        UUID eventId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID projectionId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2028-01-10T08:00:00Z");
        OfferLetterRequestedEvent event = new OfferLetterRequestedEvent(
                eventId, OfferLetterRequestedEvent.CURRENT_SCHEMA_VERSION, requestedAt,
                offerId, 1, "OFR-2028-0001", UUID.randomUUID(), "APP-2028-0001",
                "APL-2028-0001", "Tariro Moyo", "tariro@example.test", UUID.randomUUID(),
                "BSC-CS", "Computer Science", "FIRM", null,
                requestedAt.plusSeconds(86_400), LocalDate.parse("2028-08-15"),
                LocalDate.parse("2028-08-20"), LocalDate.parse("2028-08-25"), UUID.randomUUID());
        ObjectMapper objectMapper = new ObjectMapper();
        Message message = new Message(objectMapper.writeValueAsBytes(event));

        when(processedInbox.getProcessedAt()).thenReturn(requestedAt);
        when(inboxRepository.findById(eventId)).thenReturn(Optional.empty(), Optional.of(processedInbox));
        when(inboxRepository.save(any(DocumentsReportingIntegrationInbox.class))).thenReturn(claimedInbox);
        when(projectionRepository.findByOfferIdAndOfferVersionAndDeletedAtIsNull(offerId, 1))
                .thenReturn(Optional.of(projection));
        when(projection.getId()).thenReturn(projectionId);
        when(projection.getOfferNumber()).thenReturn("OFR-2028-0001");
        when(projection.getProgrammeId()).thenReturn(event.programmeId());
        when(projection.getOfferVersion()).thenReturn(1L);
        when(documentRepository.existsByOfferLetterIdAndDeletedAtIsNull(projectionId)).thenReturn(false);
        OfferLetterRequestedEventListener listener = new OfferLetterRequestedEventListener(
                inboxRepository, projectionRepository, documentRepository, objectMapper,
                Clock.fixed(requestedAt, ZoneOffset.UTC));

        listener.receive(message);
        listener.receive(message);

        verify(documentRepository, times(1)).save(any(GeneratedDocument.class));
        verify(claimedInbox, times(1)).markProcessed(requestedAt);
    }
}
