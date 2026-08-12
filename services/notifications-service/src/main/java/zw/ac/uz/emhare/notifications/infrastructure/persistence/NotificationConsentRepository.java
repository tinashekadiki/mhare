package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import zw.ac.uz.emhare.notifications.domain.model.NotificationConsent;
import zw.ac.uz.emhare.notifications.domain.model.NotificationTemplate;

import zw.ac.uz.emhare.notifications.*;
import zw.ac.uz.emhare.notifications.domain.model.*;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface NotificationConsentRepository extends JpaRepository<NotificationConsent,UUID> {
    List<NotificationConsent> findAllByOrderByRecipientKeyAscCategoryAsc();
    Optional<NotificationConsent> findFirstByRecipientKeyIgnoreCaseAndChannelAndCategoryAndEffectiveUntilIsNull(String key,NotificationTemplate.Channel channel,NotificationTemplate.Category category);
}
