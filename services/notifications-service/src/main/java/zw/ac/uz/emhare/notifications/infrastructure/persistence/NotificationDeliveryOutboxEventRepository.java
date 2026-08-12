package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import java.time.Instant;import java.util.List;import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.notifications.domain.model.NotificationDeliveryOutboxEvent;
/** @author Tinashe K */
public interface NotificationDeliveryOutboxEventRepository extends JpaRepository<NotificationDeliveryOutboxEvent,UUID>{
    @Query(value="SELECT * FROM notification_delivery_outbox WHERE status='PENDING' AND next_attempt_at<=:now ORDER BY occurred_at,id FOR UPDATE SKIP LOCKED LIMIT 25",nativeQuery=true)
    List<NotificationDeliveryOutboxEvent> lockNextDispatchBatch(@Param("now")Instant now);
}
