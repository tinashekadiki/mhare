package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import zw.ac.uz.emhare.notifications.domain.model.NotificationProviderCallback;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.domain.model.*;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface NotificationProviderCallbackRepository extends JpaRepository<NotificationProviderCallback,UUID> {
    Optional<NotificationProviderCallback> findByProviderCodeIgnoreCaseAndProviderEventId(String providerCode,String providerEventId);
    List<NotificationProviderCallback> findAllByOrderByReceivedAtDesc();
}
