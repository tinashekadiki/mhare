package zw.ac.uz.emhare.documentsreporting.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.documentsreporting.upload.UploadedDocument;
import zw.ac.uz.emhare.common.messaging.OfferLetterStoredEvent;
import zw.ac.uz.emhare.documentsreporting.document.GeneratedDocument;

/** @author Tinashe K */
@Service
public class DocumentVerificationOutboxService {
    private final DocumentsOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DocumentVerificationOutboxService(
            DocumentsOutboxEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueue(UploadedDocument document) {
        String eventIdentity = "document-verification:" + document.getId() + ":" + document.getVersion();
        UUID eventId = UUID.nameUUIDFromBytes(eventIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        DocumentVerificationChangedEvent event = new DocumentVerificationChangedEvent(
                eventId,
                DocumentVerificationChangedEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                document.getId(),
                document.getOwnerType().name(),
                document.getOwnerId(),
                document.getDocumentTypeCode(),
                document.getVerificationStatus().name(),
                document.getVerifiedByUserId(),
                document.getVerifiedAt(),
                document.getVerificationComment(),
                document.getRejectionReason(),
                document.getVersion());
        repository.save(new DocumentsOutboxEvent(
                eventId,
                EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT,
                EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT,
                serialize(event),
                occurredAt));
    }

    public void enqueueOfferLetterStored(GeneratedDocument document) {
        var offer = document.getOfferLetter();
        String identity = "offer-letter-stored:" + document.getId() + ":" + document.getVersion();
        UUID eventId = UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (repository.existsById(eventId)) return;
        Instant occurredAt = clock.instant();
        OfferLetterStoredEvent event = new OfferLetterStoredEvent(eventId,
                OfferLetterStoredEvent.CURRENT_SCHEMA_VERSION, occurredAt, offer.getOfferId(), offer.getOfferVersion(),
                document.getId(), document.getDocumentNumber(), document.getStorageBucket(), document.getStorageKey(),
                document.getChecksumSha256(), document.getGeneratedAt());
        repository.save(new DocumentsOutboxEvent(eventId, EmhareMessagingTopology.OFFER_LETTER_STORED_EVENT,
                EmhareMessagingTopology.OFFER_LETTER_STORED_EVENT, serializeObject(event), occurredAt));
    }

    private String serialize(DocumentVerificationChangedEvent event) {
        return serializeObject(event);
    }

    private String serializeObject(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Document verification event could not be serialized.", exception);
        }
    }
}
