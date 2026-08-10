package zw.ac.uz.emhare.notifications;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate,UUID> {
    List<NotificationTemplate> findAllByOrderByCodeAscTemplateVersionDesc();
    Optional<NotificationTemplate> findFirstByCodeIgnoreCaseAndChannelAndLocaleAndStatusOrderByTemplateVersionDesc(String code,NotificationTemplate.Channel channel,String locale,NotificationTemplate.Status status);
}
interface NotificationConsentRepository extends JpaRepository<NotificationConsent,UUID> {
    List<NotificationConsent> findAllByOrderByRecipientKeyAscCategoryAsc();
    Optional<NotificationConsent> findFirstByRecipientKeyIgnoreCaseAndChannelAndCategoryAndEffectiveUntilIsNull(String key,NotificationTemplate.Channel channel,NotificationTemplate.Category category);
}
interface NotificationRequestRepository extends JpaRepository<NotificationRequest,UUID> {
    @Query(value="SELECT nextval('notification_request_number_seq')",nativeQuery=true)
    long nextRequestNumber();
    Optional<NotificationRequest> findByIdempotencyKey(String key);
    Optional<NotificationRequest> findFirstByProviderCodeIgnoreCaseAndProviderMessageId(String providerCode,String providerMessageId);
    List<NotificationRequest> findAllByOrderByScheduledAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NotificationRequest r where r.status in (zw.ac.uz.emhare.notifications.NotificationRequest.Status.QUEUED,zw.ac.uz.emhare.notifications.NotificationRequest.Status.RETRY_SCHEDULED) and r.scheduledAt<=:now and r.nextAttemptAt<=:now order by case r.priority when zw.ac.uz.emhare.notifications.NotificationRequest.Priority.URGENT then 0 when zw.ac.uz.emhare.notifications.NotificationRequest.Priority.HIGH then 1 when zw.ac.uz.emhare.notifications.NotificationRequest.Priority.NORMAL then 2 else 3 end,r.nextAttemptAt,r.id")
    List<NotificationRequest> lockDue(@Param("now") Instant now,Pageable pageable);
}
interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttempt,UUID> {
    List<NotificationDeliveryAttempt> findAllByOrderByStartedAtDesc();
}
interface NotificationEventInboxRepository extends JpaRepository<NotificationEventInbox,UUID> {
    Optional<NotificationEventInbox> findBySourceServiceIgnoreCaseAndSourceEventId(String sourceService,UUID sourceEventId);
    List<NotificationEventInbox> findAllByOrderByReceivedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from NotificationEventInbox i where i.status in (zw.ac.uz.emhare.notifications.NotificationEventInbox.Status.RECEIVED,zw.ac.uz.emhare.notifications.NotificationEventInbox.Status.RETRY_SCHEDULED) and i.nextAttemptAt<=:now order by i.nextAttemptAt,i.receivedAt,i.id")
    List<NotificationEventInbox> lockDue(@Param("now") Instant now,Pageable pageable);
}
interface NotificationProviderCallbackRepository extends JpaRepository<NotificationProviderCallback,UUID> {
    Optional<NotificationProviderCallback> findByProviderCodeIgnoreCaseAndProviderEventId(String providerCode,String providerEventId);
    List<NotificationProviderCallback> findAllByOrderByReceivedAtDesc();
}
interface InAppNotificationRepository extends JpaRepository<InAppNotification,UUID> {
    Optional<InAppNotification> findByNotificationRequestId(UUID requestId);
    Optional<InAppNotification> findByIdAndRecipientUserId(UUID id,UUID recipientUserId);
    List<InAppNotification> findAllByOrderByDeliveredAtDesc();
    List<InAppNotification> findAllByRecipientUserIdOrderByDeliveredAtDesc(UUID recipientUserId);
}
