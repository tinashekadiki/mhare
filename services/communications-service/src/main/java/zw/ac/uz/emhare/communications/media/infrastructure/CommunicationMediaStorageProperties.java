package zw.ac.uz.emhare.communications.media.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** S3-compatible Communications media settings. @author Tinashe K */
@ConfigurationProperties(prefix = "emhare.communications.media")
public record CommunicationMediaStorageProperties(
    String bucket,
    URI endpoint,
    String region,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    long maximumBytes) {}
