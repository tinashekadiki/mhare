package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.domain.model.*;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface NotificationRequestRepository extends JpaRepository<NotificationRequest,UUID> {
    @Query(value="SELECT nextval('notification_request_number_seq')",nativeQuery=true)
    long nextRequestNumber();
    Optional<NotificationRequest> findByIdempotencyKey(String key);
    Optional<NotificationRequest> findFirstByProviderCodeIgnoreCaseAndProviderMessageId(String providerCode,String providerMessageId);
    List<NotificationRequest> findAllByOrderByScheduledAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NotificationRequest r where r.status in (zw.ac.uz.emhare.notifications.domain.model.NotificationRequest.Status.QUEUED,zw.ac.uz.emhare.notifications.domain.model.NotificationRequest.Status.RETRY_SCHEDULED) and r.scheduledAt<=:now and r.nextAttemptAt<=:now order by case r.priority when zw.ac.uz.emhare.notifications.domain.model.NotificationRequest.Priority.URGENT then 0 when zw.ac.uz.emhare.notifications.domain.model.NotificationRequest.Priority.HIGH then 1 when zw.ac.uz.emhare.notifications.domain.model.NotificationRequest.Priority.NORMAL then 2 else 3 end,r.nextAttemptAt,r.id")
    List<NotificationRequest> lockDue(@Param("now") Instant now,Pageable pageable);
}
