package zw.ac.uz.emhare.notifications;

import java.time.Clock;import java.util.UUID;
import org.springframework.stereotype.Service;import tools.jackson.core.JacksonException;import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;import zw.ac.uz.emhare.notifications.domain.model.*;import zw.ac.uz.emhare.notifications.infrastructure.persistence.NotificationDeliveryOutboxEventRepository;
/** @author Tinashe K */
@Service
public class NotificationDeliveryOutboxService{
    private final NotificationDeliveryOutboxEventRepository repository;private final ObjectMapper mapper;private final Clock clock;
    public NotificationDeliveryOutboxService(NotificationDeliveryOutboxEventRepository repository,ObjectMapper mapper,Clock clock){this.repository=repository;this.mapper=mapper;this.clock=clock;}
    public void enqueue(NotificationRequest request,String status,String providerMessageId,String failureReason){
        if(request.getSourceEventId()==null)return;var now=clock.instant();UUID eventId=UUID.randomUUID();
        NotificationDeliveryEvent event=new NotificationDeliveryEvent(eventId,NotificationDeliveryEvent.CURRENT_SCHEMA_VERSION,now,
                request.getSourceEventId(),request.getAttemptCount(),status,providerMessageId,failureReason);
        try{repository.save(new NotificationDeliveryOutboxEvent(eventId,EmhareMessagingTopology.NOTIFICATION_DELIVERY_EVENT,
                EmhareMessagingTopology.NOTIFICATION_DELIVERY_EVENT,mapper.writeValueAsString(event),now));}
        catch(JacksonException exception){throw new IllegalStateException("Notification delivery event could not be serialized.",exception);}
    }
}
