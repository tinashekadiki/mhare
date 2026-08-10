package zw.ac.uz.emhare.documentsreporting.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class UploadedDocumentTest {
    private final UUID ownerId = UUID.randomUUID();
    private final UUID uploaderId = UUID.randomUUID();

    @Test
    void recordsAnImmutableVerificationDecision() {
        UploadedDocument document = pendingDocument();
        UUID verifierId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-08-08T18:00:00Z");

        document.verify(verifierId, "Identity details match the application.", 0, verifiedAt);

        assertEquals(UploadedDocument.VerificationStatus.VERIFIED, document.getVerificationStatus());
        assertEquals(verifierId, document.getVerifiedByUserId());
        assertEquals(verifiedAt, document.getVerifiedAt());
        assertThrows(IllegalStateException.class,
                () -> document.reject(verifierId, "A later conflicting decision.", 0, verifiedAt));
    }

    @Test
    void requiresDetailedRejectionEvidenceAndCurrentVersion() {
        UploadedDocument document = pendingDocument();
        UUID verifierId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-08-08T18:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> document.reject(verifierId, "Invalid", 0, decidedAt));
        assertThrows(IllegalStateException.class,
                () -> document.reject(verifierId, "The identity number is unreadable.", 5, decidedAt));
    }

    private UploadedDocument pendingDocument() {
        return new UploadedDocument(
                UploadedDocument.OwnerType.APPLICATION,
                ownerId,
                "NATIONAL_ID",
                "identity.pdf",
                "documents",
                "uploads/identity.pdf",
                null,
                "application/pdf",
                100,
                "a".repeat(64),
                uploaderId,
                Instant.parse("2026-08-08T17:00:00Z"),
                null);
    }
}
