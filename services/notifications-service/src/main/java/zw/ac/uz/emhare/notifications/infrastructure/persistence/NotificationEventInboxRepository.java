package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import zw.ac.uz.emhare.notifications.domain.model.NotificationEventInbox;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.domain.model.*;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface NotificationEventInboxRepository extends JpaRepository<NotificationEventInbox,UUID> {
    Optional<NotificationEventInbox> findBySourceServiceIgnoreCaseAndSourceEventId(String sourceService,UUID sourceEventId);
    List<NotificationEventInbox> findAllByOrderByReceivedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from NotificationEventInbox i where i.status in (zw.ac.uz.emhare.notifications.domain.model.NotificationEventInbox.Status.RECEIVED,zw.ac.uz.emhare.notifications.domain.model.NotificationEventInbox.Status.RETRY_SCHEDULED) and i.nextAttemptAt<=:now order by i.nextAttemptAt,i.receivedAt,i.id")
    List<NotificationEventInbox> lockDue(@Param("now") Instant now,Pageable pageable);
}
