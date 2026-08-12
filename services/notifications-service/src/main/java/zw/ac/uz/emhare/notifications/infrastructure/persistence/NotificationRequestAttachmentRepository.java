package zw.ac.uz.emhare.notifications.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.notifications.domain.model.NotificationRequestAttachment;

/** @author Tinashe K */
public interface NotificationRequestAttachmentRepository extends JpaRepository<NotificationRequestAttachment, UUID> {
    List<NotificationRequestAttachment> findAllByNotificationRequestIdOrderByAttachmentSequenceAsc(UUID requestId);
}
