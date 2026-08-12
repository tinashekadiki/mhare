package zw.ac.uz.emhare.admissions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable generated-document version belonging to one admission offer. @author Tinashe K */
@Audited
@Entity
@Table(name = "offer_document_versions", uniqueConstraints = @UniqueConstraint(
        name = "uk_offer_document_version", columnNames = {"offer_id", "document_version"}))
@SQLRestriction("deleted_at IS NULL")
public class OfferDocumentVersion extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private AdmissionOffer offer;
    @Column(name = "document_version", nullable = false)
    private int documentVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferDocumentVersionStatus status;
    @Column(name = "generated_document_id") private UUID generatedDocumentId;
    @Column(name = "document_number", length = 80) private String documentNumber;
    @Column(name = "storage_bucket", length = 120) private String storageBucket;
    @Column(name = "storage_key", length = 500) private String storageKey;
    @Column(name = "checksum_sha256", length = 64) private String checksumSha256;
    @Column(name = "failure_reason", length = 1000) private String failureReason;
    @Column(name = "requested_by_user_id", nullable = false) private UUID requestedByUserId;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "stored_at") private Instant storedAt;

    protected OfferDocumentVersion() { }

    public OfferDocumentVersion(AdmissionOffer offer, int documentVersion, UUID requestedByUserId, Instant requestedAt) {
        if (documentVersion < 1 || requestedByUserId == null || requestedAt == null) {
            throw new IllegalArgumentException("Offer document version, requester, and request time are required.");
        }
        this.offer = offer;
        this.documentVersion = documentVersion;
        this.requestedByUserId = requestedByUserId;
        this.requestedAt = requestedAt;
        this.status = OfferDocumentVersionStatus.REQUESTED;
    }

    public void store(UUID generatedDocumentId, String documentNumber, String storageBucket,
            String storageKey, String checksumSha256, Instant storedAt) {
        if (status == OfferDocumentVersionStatus.STORED) {
            if (!this.generatedDocumentId.equals(generatedDocumentId)) {
                throw new IllegalStateException("Offer document version is already linked to another document.");
            }
            return;
        }
        if (status != OfferDocumentVersionStatus.REQUESTED) {
            throw new IllegalStateException("Only a requested offer document version can be stored.");
        }
        this.generatedDocumentId = required(generatedDocumentId, "Generated document");
        this.documentNumber = required(documentNumber, "Document number");
        this.storageBucket = required(storageBucket, "Storage bucket");
        this.storageKey = required(storageKey, "Storage key");
        this.checksumSha256 = required(checksumSha256, "Document checksum");
        this.storedAt = required(storedAt, "Stored time");
        this.status = OfferDocumentVersionStatus.STORED;
    }

    public void fail(String reason) {
        if (status != OfferDocumentVersionStatus.REQUESTED) {
            throw new IllegalStateException("Only a requested offer document version can fail.");
        }
        failureReason = required(reason, "Generation failure reason");
        status = OfferDocumentVersionStatus.FAILED;
    }

    private static <T> T required(T value, String label) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value instanceof String text ? (T) text.trim() : value;
    }

    public AdmissionOffer getOffer() { return offer; }
    public int getDocumentVersion() { return documentVersion; }
    public OfferDocumentVersionStatus getStatus() { return status; }
    public UUID getGeneratedDocumentId() { return generatedDocumentId; }
    public String getDocumentNumber() { return documentNumber; }
    public String getStorageBucket() { return storageBucket; }
    public String getStorageKey() { return storageKey; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getFailureReason() { return failureReason; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStoredAt() { return storedAt; }
}
