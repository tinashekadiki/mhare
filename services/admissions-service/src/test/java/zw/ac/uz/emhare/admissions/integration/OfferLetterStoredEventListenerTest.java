package zw.ac.uz.emhare.admissions.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.OfferLetterStoredEvent;

/** @author Tinashe K */
class OfferLetterStoredEventListenerTest {

    @Test
    void duplicateStoredDocumentEventLinksTheOfferOnlyOnce() throws Exception {
        AdmissionsIntegrationInbox inbox = mock(AdmissionsIntegrationInbox.class);
        AdmissionsSelectionOfferService offerService = mock(AdmissionsSelectionOfferService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant storedAt = Instant.parse("2028-01-10T08:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID generatedDocumentId = UUID.randomUUID();
        OfferLetterStoredEvent event = new OfferLetterStoredEvent(
                eventId,
                OfferLetterStoredEvent.CURRENT_SCHEMA_VERSION,
                storedAt,
                offerId,
                2,
                generatedDocumentId,
                "OFFER-OFR-2028-0001",
                "documents",
                "official-offers/APP-2028-0001/OFFER-OFR-2028-0001.pdf",
                "a".repeat(64),
                storedAt);
        String payload = objectMapper.writeValueAsString(event);
        Message message = new Message(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(inbox.claim(eq(eventId), eq(EmhareMessagingTopology.OFFER_LETTER_STORED_EVENT),
                eq("documents-reporting-service"), eq(payload), any(Instant.class))).thenReturn(true, false);
        OfferLetterStoredEventListener listener = new OfferLetterStoredEventListener(
                inbox, offerService, objectMapper, Clock.fixed(storedAt, ZoneOffset.UTC));

        listener.receive(message);
        listener.receive(message);

        verify(offerService, times(1)).linkStoredOfferLetter(offerId, 2, generatedDocumentId);
        verify(inbox, times(1)).markProcessed(eventId, storedAt);
    }
}
