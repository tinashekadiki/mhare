package zw.ac.uz.emhare.documentsreporting.integration;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.OfferLetterProjectionRepository;

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
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.OfferLetterProjection;

/** @author Tinashe K */
@Component
public class OfferLetterRequestedEventListener {
    private final DocumentsReportingIntegrationInboxRepository inboxRepository;
    private final OfferLetterProjectionRepository projectionRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final DocumentVerificationOutboxService outboxService;
    private final ObjectMapper objectMapper; private final Clock clock;
    public OfferLetterRequestedEventListener(DocumentsReportingIntegrationInboxRepository inboxRepository,
            OfferLetterProjectionRepository projectionRepository, GeneratedDocumentRepository documentRepository,
            DocumentVerificationOutboxService outboxService, ObjectMapper objectMapper, Clock clock) {
        this.inboxRepository=inboxRepository; this.projectionRepository=projectionRepository;
        this.documentRepository=documentRepository; this.outboxService=outboxService;
        this.objectMapper=objectMapper; this.clock=clock;
    }
    @RabbitListener(queues=EmhareMessagingTopology.OFFER_LETTER_REQUESTED_DOCUMENTS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload=new String(message.getBody(), StandardCharsets.UTF_8); OfferLetterRequestedEvent event;
        try { event=objectMapper.readValue(payload, OfferLetterRequestedEvent.class); }
        catch(JacksonException e){throw new IllegalArgumentException("Offer-letter request is invalid.",e);}
        if(event.schemaVersion()!=OfferLetterRequestedEvent.CURRENT_SCHEMA_VERSION || event.offerId()==null
                || event.offerNumber()==null || event.contentSnapshot()==null || event.requestedByUserId()==null)
            throw new IllegalArgumentException("Offer-letter request is invalid or unsupported.");
        var existing=inboxRepository.findById(event.eventId());
        if(existing.isPresent() && existing.get().getProcessedAt()!=null)return;
        DocumentsReportingIntegrationInbox inbox=existing.orElseGet(()->inboxRepository.save(
                new DocumentsReportingIntegrationInbox(event.eventId(),EmhareMessagingTopology.OFFER_LETTER_REQUESTED_EVENT,
                        "admissions-service",payload,clock.instant())));
        OfferLetterProjection projection=projectionRepository.findByOfferIdAndDocumentVersionAndDeletedAtIsNull(event.offerId(),event.documentVersion())
                .orElseGet(()->projectionRepository.saveAndFlush(new OfferLetterProjection(event)));
        GeneratedDocument document=documentRepository.findByOfferLetterIdAndDeletedAtIsNull(projection.getId())
                .orElseGet(()->documentRepository.save(new GeneratedDocument(projection,clock.instant())));
        if(document.getStatus()==GeneratedDocument.Status.STORED) outboxService.enqueueOfferLetterStored(document);
        inbox.markProcessed(clock.instant());
    }
}
