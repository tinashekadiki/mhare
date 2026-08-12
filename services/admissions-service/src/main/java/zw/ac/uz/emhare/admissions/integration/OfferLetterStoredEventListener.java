package zw.ac.uz.emhare.admissions.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsSelectionOfferService;
import zw.ac.uz.emhare.admissions.application.DirectAdmissionOfferService;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.OfferLetterStoredEvent;

/** @author Tinashe K */
@Component
public class OfferLetterStoredEventListener {
    private final AdmissionsIntegrationInbox inbox;
    private final AdmissionsSelectionOfferService offerService;
    private final DirectAdmissionOfferService directOfferService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public OfferLetterStoredEventListener(AdmissionsIntegrationInbox inbox,
            DirectAdmissionOfferService directOfferService, ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox; this.offerService = null; this.directOfferService = directOfferService;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    OfferLetterStoredEventListener(AdmissionsIntegrationInbox inbox,
            AdmissionsSelectionOfferService offerService, ObjectMapper objectMapper, Clock clock) {
        this.inbox = inbox; this.offerService = offerService; this.directOfferService = null;
        this.objectMapper = objectMapper; this.clock = clock;
    }
    @RabbitListener(queues = EmhareMessagingTopology.OFFER_LETTER_STORED_ADMISSIONS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        OfferLetterStoredEvent event;
        try { event = objectMapper.readValue(payload, OfferLetterStoredEvent.class); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Offer-letter stored event is invalid.", exception); }
        if (event.schemaVersion() != OfferLetterStoredEvent.CURRENT_SCHEMA_VERSION
                || event.offerId() == null || event.generatedDocumentId() == null || event.storedAt() == null) {
            throw new IllegalArgumentException("Offer-letter stored event is invalid or unsupported.");
        }
        if (!inbox.claim(event.eventId(), EmhareMessagingTopology.OFFER_LETTER_STORED_EVENT,
                "documents-reporting-service", payload, clock.instant())) return;
        if (directOfferService == null) {
            offerService.linkStoredOfferLetter(event.offerId(), event.offerVersion(), event.generatedDocumentId());
        } else {
            directOfferService.linkStoredDocument(event.offerId(), event.offerVersion(), event.documentVersion(),
                    event.generatedDocumentId(), event.documentNumber(), event.storageBucket(), event.storageKey(),
                    event.checksumSha256(), event.storedAt());
        }
        inbox.markProcessed(event.eventId(), clock.instant());
    }
}
