package zw.ac.uz.emhare.notifications;

import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Prevents an unconfigured production deployment from falsely reporting delivery. @author Tinashe K */
@Component
@ConditionalOnProperty(name="emhare.notifications.provider",havingValue="disabled",matchIfMissing=true)
public class DisabledNotificationDeliveryProvider implements NotificationDeliveryProvider {
    public String code(){return "DISABLED";}
    public DeliveryResult deliver(NotificationRequest request){return DeliveryResult.failed(false,"PROVIDER_NOT_CONFIGURED","No notification delivery provider is configured for this deployment.",Map.of("channel",request.getChannel().name()));}
}
