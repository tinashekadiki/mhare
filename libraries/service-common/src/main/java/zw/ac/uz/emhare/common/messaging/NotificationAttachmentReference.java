package zw.ac.uz.emhare.common.messaging;

import java.util.UUID;

/** Checksum-addressed attachment stored by Documents and Reporting. @author Tinashe K */
public record NotificationAttachmentReference(UUID generatedDocumentId, String documentNumber,
        String storageBucket, String storageKey, String checksumSha256, String fileName, String contentType) { }
