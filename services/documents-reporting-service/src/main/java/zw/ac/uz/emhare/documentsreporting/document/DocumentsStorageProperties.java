package zw.ac.uz.emhare.documentsreporting.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @author Tinashe K */
@ConfigurationProperties(prefix = "emhare.documents.storage")
public record DocumentsStorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccess,
        long downloadUrlValiditySeconds,
        long maximumUploadBytes) {
}
