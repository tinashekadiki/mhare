package zw.ac.uz.emhare.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @author Tinashe K */
@ConfigurationProperties(prefix = "emhare.notifications.attachments.storage")
public record NotificationAttachmentStorageProperties(String endpoint, String region,
        String accessKey, String secretKey, boolean pathStyleAccess) { }
