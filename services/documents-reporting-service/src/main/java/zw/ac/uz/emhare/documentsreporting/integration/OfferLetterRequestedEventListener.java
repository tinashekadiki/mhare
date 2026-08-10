package zw.ac.uz.emhare.documentsreporting.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.common.messaging.OfferLetterRequestedEvent;
import zw.ac.uz.emhare.documentsreporting.document.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.document.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjection;
import zw.ac.uz.emhare.documentsreporting.projection.OfferLetterProjectionRepository;

/** @author Tinashe K */
@Component
public class OfferLetterRequestedEventListener {
    private final DocumentsReportingIntegrationInboxRepository inboxRepository;
    private final OfferLetterProjectionRepository projectionRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ObjectMapper objectMapper; private final Clock clock;
    public OfferLetterRequestedEventListener(DocumentsReportingIntegrationInboxRepository inboxRepository,
            OfferLetterProjectionRepository projectionRepository, GeneratedDocumentRepository documentRepository,
            ObjectMapper objectMapper, Clock clock) {
        this.inboxRepository=inboxRepository; this.projectionRepository=projectionRepository;
        this.documentRepository=documentRepository; this.objectMapper=objectMapper; this.clock=clock;
    }
    @RabbitListener(queues=EmhareMessagingTopology.OFFER_LETTER_REQUESTED_DOCUMENTS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload=new String(message.getBody(), StandardCharsets.UTF_8); OfferLetterRequestedEvent event;
        try { event=objectMapper.readValue(payload, OfferLetterRequestedEvent.class); }
        catch(JacksonException e){throw new IllegalArgumentException("Offer-letter request is invalid.",e);}
        if(event.schemaVersion()!=OfferLetterRequestedEvent.CURRENT_SCHEMA_VERSION || event.offerId()==null
                || event.offerNumber()==null || event.requestedByUserId()==null) throw new IllegalArgumentException("Offer-letter request is invalid or unsupported.");
        var existing=inboxRepository.findById(event.eventId());
        if(existing.isPresent() && existing.get().getProcessedAt()!=null)return;
        DocumentsReportingIntegrationInbox inbox=existing.orElseGet(()->inboxRepository.save(
                new DocumentsReportingIntegrationInbox(event.eventId(),EmhareMessagingTopology.OFFER_LETTER_REQUESTED_EVENT,
                        "admissions-service",payload,clock.instant())));
        OfferLetterProjection projection=projectionRepository.findByOfferIdAndOfferVersionAndDeletedAtIsNull(event.offerId(),event.offerVersion())
                .orElseGet(()->projectionRepository.saveAndFlush(new OfferLetterProjection(event)));
        if(!documentRepository.existsByOfferLetterIdAndDeletedAtIsNull(projection.getId()))
            documentRepository.save(new GeneratedDocument(projection,clock.instant()));
        inbox.markProcessed(clock.instant());
    }
}
