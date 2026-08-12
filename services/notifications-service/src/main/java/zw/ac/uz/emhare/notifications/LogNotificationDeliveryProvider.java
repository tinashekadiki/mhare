package zw.ac.uz.emhare.notifications;

import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development-safe provider. Production deployments must supply a provider bean. @author Tinashe K */
@Component @ConditionalOnProperty(name="emhare.notifications.provider",havingValue="local-log")
public class LogNotificationDeliveryProvider implements NotificationDeliveryProvider {
    private static final Logger logger=LoggerFactory.getLogger(LogNotificationDeliveryProvider.class);
    public String code(){return "LOCAL_LOG";}
    public DeliveryResult deliver(NotificationRequest request){String providerId="local-"+UUID.randomUUID();logger.info("Recorded local notification {} on channel {} for recipient key {}",request.getRequestNumber(),request.getChannel(),request.getRecipientKey());return DeliveryResult.sent(providerId,Map.of("mode","local-log"));}
}
