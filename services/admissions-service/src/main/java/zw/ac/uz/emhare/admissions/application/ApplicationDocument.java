package zw.ac.uz.emhare.admissions.application;

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
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.DocumentVerificationChangedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(
        name = "application_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_documents_requirement",
                columnNames = {"application_id", "requirement_code", "document_id"}))
public class ApplicationDocument extends AuditableEntity {

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "requirement_code", nullable = false, length = 80)
    private String requirementCode;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationStatus status;

    @Column(name = "document_file_name", length = 255)
    private String documentFileName;

    @Column(name = "document_mime_type", length = 100)
    private String documentMimeType;

    @Column(name = "document_checksum_sha256", length = 64)
    private String documentChecksumSha256;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "supersedes_application_document_id")
    private UUID supersedesApplicationDocumentId;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "last_verification_event_id")
    private UUID lastVerificationEventId;

    @Column(name = "last_document_version", nullable = false)
    private long lastDocumentVersion;

    protected ApplicationDocument() {
    }

    public ApplicationDocument(
            Application application,
            UUID documentId,
            String requirementCode,
            boolean required,
            String documentFileName,
            String documentMimeType,
            String documentChecksumSha256,
            Instant linkedAt,
            UUID supersedesApplicationDocumentId) {
        this.application = application;
        this.documentId = documentId;
        this.requirementCode = requirementCode;
        this.required = required;
        this.status = VerificationStatus.PENDING;
        this.documentFileName = documentFileName;
        this.documentMimeType = documentMimeType;
        this.documentChecksumSha256 = documentChecksumSha256;
        this.linkedAt = linkedAt;
        this.current = true;
        this.supersedesApplicationDocumentId = supersedesApplicationDocumentId;
    }

    public void supersede() {
        if (!current) throw new IllegalStateException("Application document is already superseded.");
        if (status != VerificationStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected application document can be replaced.");
        }
        current = false;
    }

    public boolean applyVerification(DocumentVerificationChangedEvent event) {
        if (!documentId.equals(event.documentId())) return false;
        if (event.documentVersion() <= lastDocumentVersion) return false;
        VerificationStatus incomingStatus;
        try {
            incomingStatus = VerificationStatus.valueOf(event.verificationStatus());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported document verification status.", exception);
        }
        if (incomingStatus == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Document verification events must contain a decision.");
        }
        status = incomingStatus;
        verifiedByUserId = event.verifiedByUserId();
        verifiedAt = event.verifiedAt();
        rejectionReason = event.rejectionReason();
        lastVerificationEventId = event.eventId();
        lastDocumentVersion = event.documentVersion();
        return true;
    }

    public Application getApplication() { return application; }
    public UUID getDocumentId() { return documentId; }
    public String getRequirementCode() { return requirementCode; }
    public boolean isRequired() { return required; }
    public VerificationStatus getStatus() { return status; }
    public String getDocumentFileName() { return documentFileName; }
    public String getDocumentMimeType() { return documentMimeType; }
    public String getDocumentChecksumSha256() { return documentChecksumSha256; }
    public Instant getLinkedAt() { return linkedAt; }
    public boolean isCurrent() { return current; }
    public UUID getSupersedesApplicationDocumentId() { return supersedesApplicationDocumentId; }
    public UUID getVerifiedByUserId() { return verifiedByUserId; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public long getLastDocumentVersion() { return lastDocumentVersion; }
}
