package zw.ac.uz.emhare.notifications.infrastructure.persistence;

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
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate,UUID> {
    List<NotificationTemplate> findAllByOrderByCodeAscTemplateVersionDesc();
    Optional<NotificationTemplate> findFirstByCodeIgnoreCaseAndChannelAndLocaleAndStatusOrderByTemplateVersionDesc(String code,NotificationTemplate.Channel channel,String locale,NotificationTemplate.Status status);
}
