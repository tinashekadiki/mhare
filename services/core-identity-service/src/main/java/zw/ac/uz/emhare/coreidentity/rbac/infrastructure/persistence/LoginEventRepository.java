package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LoginEvent;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
    List<LoginEvent> findTop100ByDeletedAtIsNullOrderByOccurredAtDesc();
    boolean existsByKeycloakUserIdAndIdentitySessionIdAndDeletedAtIsNull(UUID keycloakUserId, String identitySessionId);
    long countByOccurredAtGreaterThanEqualAndDeletedAtIsNull(Instant occurredAt);
}
