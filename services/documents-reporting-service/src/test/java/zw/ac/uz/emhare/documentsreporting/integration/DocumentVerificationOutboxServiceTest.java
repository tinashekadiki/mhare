package zw.ac.uz.emhare.documentsreporting.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.common.messaging.EmhareMessagingTopology;
import zw.ac.uz.emhare.documentsreporting.upload.UploadedDocument;

/** @author Tinashe K */
class DocumentVerificationOutboxServiceTest {

    @Test
    void rejectionCreatesOneDeterministicAuditableOutboxEvent() throws Exception {
        DocumentsOutboxEventRepository repository = org.mockito.Mockito.mock(DocumentsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(false, true);
        when(repository.save(any(DocumentsOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant occurredAt = Instant.parse("2026-08-08T18:30:00Z");
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentVerificationOutboxService service = new DocumentVerificationOutboxService(
                repository,
                objectMapper,
                Clock.fixed(occurredAt, ZoneOffset.UTC));
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        UploadedDocument document = pendingDocument(documentId, ownerId);
        document.reject(verifierId, "The identity number is not readable.", 0, occurredAt);

        service.enqueue(document);
        service.enqueue(document);

        ArgumentCaptor<DocumentsOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(DocumentsOutboxEvent.class);
        verify(repository, times(1)).save(outboxCaptor.capture());
        DocumentsOutboxEvent outboxEvent = outboxCaptor.getValue();
        assertEquals(EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT, outboxEvent.getEventType());
        assertEquals(EmhareMessagingTopology.DOCUMENT_VERIFICATION_CHANGED_EVENT, outboxEvent.getRoutingKey());
        DocumentVerificationChangedEvent event = objectMapper.readValue(
                outboxEvent.getPayload(),
                DocumentVerificationChangedEvent.class);
        assertEquals(outboxEvent.getId(), event.eventId());
        assertEquals(occurredAt, event.occurredAt());
        assertEquals(documentId, event.documentId());
        assertEquals("APPLICATION", event.ownerType());
        assertEquals(ownerId, event.ownerId());
        assertEquals("NATIONAL_ID", event.documentTypeCode());
        assertEquals("REJECTED", event.verificationStatus());
        assertEquals(verifierId, event.verifiedByUserId());
        assertEquals("The identity number is not readable.", event.rejectionReason());
        assertEquals(0, event.documentVersion());
    }

    @Test
    void existingEventIsNotWrittenAgain() {
        DocumentsOutboxEventRepository repository = org.mockito.Mockito.mock(DocumentsOutboxEventRepository.class);
        when(repository.existsById(any(UUID.class))).thenReturn(true);
        DocumentVerificationOutboxService service = new DocumentVerificationOutboxService(
                repository,
                new ObjectMapper(),
                Clock.systemUTC());

        service.enqueue(pendingDocument(UUID.randomUUID(), UUID.randomUUID()));

        verify(repository, never()).save(any(DocumentsOutboxEvent.class));
    }

    private UploadedDocument pendingDocument(UUID documentId, UUID ownerId) {
        UploadedDocument document = new UploadedDocument(
                UploadedDocument.OwnerType.APPLICATION,
                ownerId,
                "NATIONAL_ID",
                "identity.pdf",
                "documents",
                "uploads/application/identity.pdf",
                "object-version-1",
                "application/pdf",
                100,
                "a".repeat(64),
                UUID.randomUUID(),
                Instant.parse("2026-08-08T18:00:00Z"),
                null);
        ReflectionTestUtils.setField(document, "id", documentId);
        return document;
    }
}
