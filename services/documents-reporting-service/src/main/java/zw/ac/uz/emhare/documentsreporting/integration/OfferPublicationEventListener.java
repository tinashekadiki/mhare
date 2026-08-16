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
import zw.ac.uz.emhare.common.messaging.OfferPublicationEvent;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.GeneratedDocumentRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging.DocumentsReportingIntegrationInboxRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedOfferLetterProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedOfferLetterProjection;

/** Idempotent current offer-publication projection listener. @author Tinashe K */
@Component
public class OfferPublicationEventListener {
    private final DocumentsReportingIntegrationInboxRepository inboxRepository;
    private final PublishedOfferLetterProjectionRepository projectionRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    public OfferPublicationEventListener(DocumentsReportingIntegrationInboxRepository inboxRepository,
            PublishedOfferLetterProjectionRepository projectionRepository, GeneratedDocumentRepository documentRepository,
            ObjectMapper objectMapper, Clock clock){this.inboxRepository=inboxRepository;this.projectionRepository=projectionRepository;
        this.documentRepository=documentRepository;this.objectMapper=objectMapper;this.clock=clock;}
    @RabbitListener(queues=EmhareMessagingTopology.OFFER_PUBLICATION_DOCUMENTS_QUEUE)
    @Transactional
    public void receive(Message message){
        String payload=new String(message.getBody(),StandardCharsets.UTF_8);OfferPublicationEvent event;
        try{event=objectMapper.readValue(payload,OfferPublicationEvent.class);}catch(JacksonException exception){throw new IllegalArgumentException("Offer publication event is invalid.",exception);}
        if(event.schemaVersion()!=OfferPublicationEvent.CURRENT_SCHEMA_VERSION||event.eventId()==null||event.generatedDocumentId()==null
                ||event.offerId()==null||event.intakeId()==null||event.programmeId()==null)throw new IllegalArgumentException("Offer publication event is invalid or unsupported.");
        var existingInbox=inboxRepository.findById(event.eventId());if(existingInbox.isPresent()&&existingInbox.get().getProcessedAt()!=null)return;
        var inbox=existingInbox.orElseGet(()->inboxRepository.save(new DocumentsReportingIntegrationInbox(event.eventId(),
                EmhareMessagingTopology.OFFER_PUBLICATION_EVENT,"admissions-service",payload,clock.instant())));
        if(projectionRepository.findBySourceEventId(event.eventId()).isEmpty()){
            var currentPublication=projectionRepository.findByOfferIdAndCurrentPublicationTrue(event.offerId());
            if(currentPublication.isPresent()
                    && currentPublication.get().getGeneratedDocument().getId().equals(event.generatedDocumentId())){
                currentPublication.get().synchronizeOfferStatus(event.offerStatus());
                inbox.markProcessed(clock.instant());
                return;
            }
            currentPublication.ifPresent(current->{
                current.supersede(event.publishedAt());
                projectionRepository.flush();
            });
            var document=documentRepository.findByIdAndDeletedAtIsNull(event.generatedDocumentId())
                    .orElseThrow(()->new IllegalStateException("Published generated document was not found."));
            projectionRepository.save(new PublishedOfferLetterProjection(event,document));
        }
        inbox.markProcessed(clock.instant());
    }
}
