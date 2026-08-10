package zw.ac.uz.emhare.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Persisted user-facing in-application notification. @author Tinashe K */
@Audited
@Entity
@Table(name = "in_app_notifications")
@SQLRestriction("deleted_at IS NULL")
public class InAppNotification extends AuditableEntity {

    @Column(name = "notification_request_id", nullable = false, unique = true)
    private UUID notificationRequestId;
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;
    @Column(name = "recipient_key", nullable = false, length = 160)
    private String recipientKey;
    @Column(length = 500)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "read_by_user_id")
    private UUID readByUserId;

    protected InAppNotification() {
    }

    public InAppNotification(NotificationRequest request, Instant deliveredAt) {
        this.notificationRequestId = request.getId();
        this.recipientUserId = request.getRecipientUserId();
        this.recipientKey = request.getRecipientKey();
        this.title = request.getSubject();
        this.body = request.getBody();
        this.deliveredAt = deliveredAt;
    }

    public void markRead(UUID actorUserId, Instant now, long expectedVersion) {
        NotificationValues.version(getVersion(), expectedVersion, "In-app notification");
        if (recipientUserId == null || !recipientUserId.equals(actorUserId)) {
            throw new IllegalArgumentException("In-app notification was not found.");
        }
        if (readAt == null) {
            readAt = now;
            readByUserId = actorUserId;
        }
    }

    public UUID getNotificationRequestId() { return notificationRequestId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getRecipientKey() { return recipientKey; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public UUID getReadByUserId() { return readByUserId; }
}
