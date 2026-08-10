package zw.ac.uz.emhare.admissions.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.admissions.application.AdmissionsDocumentService;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;

/** @author Tinashe K */
@Component
public class DocumentVerificationChangedEventListener {
    private final AdmissionsIntegrationInbox inbox;
    private final AdmissionsDocumentService documentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DocumentVerificationChangedEventListener(
            AdmissionsIntegrationInbox inbox,
            AdmissionsDocumentService documentService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.inbox = inbox;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_ADMISSIONS_QUEUE)
    @Transactional
    public void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        DocumentVerificationChangedEvent event = deserialize(payload);
        validate(event);
        if (!inbox.claim(
                event.eventId(),
                EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT,
                "documents-reporting-service",
                payload,
                clock.instant())) {
            return;
        }
        documentService.applyVerification(event);
        inbox.markProcessed(event.eventId(), clock.instant());
    }

    private DocumentVerificationChangedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DocumentVerificationChangedEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Document verification event payload is invalid.", exception);
        }
    }

    private void validate(DocumentVerificationChangedEvent event) {
        if (event.eventId() == null
                || event.schemaVersion() != DocumentVerificationChangedEvent.CURRENT_SCHEMA_VERSION
                || event.documentId() == null
                || event.ownerId() == null
                || event.ownerType() == null
                || event.documentTypeCode() == null
                || event.verificationStatus() == null
                || event.verifiedByUserId() == null
                || event.verifiedAt() == null
                || event.documentVersion() <= 0
                || (!"VERIFIED".equals(event.verificationStatus())
                    && !"REJECTED".equals(event.verificationStatus()))) {
            throw new IllegalArgumentException("Document verification event contract is invalid or unsupported.");
        }
        if ("REJECTED".equals(event.verificationStatus())
                && (event.rejectionReason() == null || event.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejected document event requires rejection evidence.");
        }
    }
}
