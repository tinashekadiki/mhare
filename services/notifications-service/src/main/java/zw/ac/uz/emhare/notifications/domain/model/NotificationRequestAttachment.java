package zw.ac.uz.emhare.notifications.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.NotificationAttachmentReference;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Immutable checksum-addressed notification attachment reference. @author Tinashe K */
@Audited
@Entity
@Table(name = "notification_request_attachments")
@SQLRestriction("deleted_at IS NULL")
public class NotificationRequestAttachment extends AuditableEntity {
    @Column(name = "notification_request_id", nullable = false) private UUID notificationRequestId;
    @Column(name = "attachment_sequence", nullable = false) private int attachmentSequence;
    @Column(name = "source_document_id", nullable = false) private UUID sourceDocumentId;
    @Column(name = "file_name", nullable = false, length = 240) private String fileName;
    @Column(name = "content_type", nullable = false, length = 160) private String contentType;
    @Column(name = "checksum_sha256", nullable = false, length = 64) private String checksumSha256;
    @Column(name = "download_url", nullable = false, length = 2000) private String storageUri;

    protected NotificationRequestAttachment() { }

    public NotificationRequestAttachment(UUID requestId, int sequence, NotificationAttachmentReference reference) {
        if (requestId == null || sequence < 1 || reference == null || reference.generatedDocumentId() == null
                || blank(reference.storageBucket()) || blank(reference.storageKey()) || blank(reference.checksumSha256())
                || blank(reference.fileName()) || blank(reference.contentType())) {
            throw new IllegalArgumentException("A complete stored attachment reference is required.");
        }
        this.notificationRequestId = requestId;
        this.attachmentSequence = sequence;
        this.sourceDocumentId = reference.generatedDocumentId();
        this.fileName = reference.fileName().trim().replaceAll("[\\r\\n\\\"/]", "-");
        this.contentType = reference.contentType().trim();
        this.checksumSha256 = reference.checksumSha256().trim().toLowerCase();
        this.storageUri = "s3://" + reference.storageBucket().trim() + "/" + reference.storageKey().trim();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public UUID getNotificationRequestId() { return notificationRequestId; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getStorageUri() { return storageUri; }
}
