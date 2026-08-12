package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import zw.ac.uz.emhare.notifications.domain.model.InAppNotification;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.domain.model.*;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface InAppNotificationRepository extends JpaRepository<InAppNotification,UUID> {
    Optional<InAppNotification> findByNotificationRequestId(UUID requestId);
    Optional<InAppNotification> findByIdAndRecipientUserId(UUID id,UUID recipientUserId);
    List<InAppNotification> findAllByOrderByDeliveredAtDesc();
    List<InAppNotification> findAllByRecipientUserIdOrderByDeliveredAtDesc(UUID recipientUserId);
}
