package zw.ac.uz.emhare.coreidentity.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.OfficialNameSynchronization;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.OfficialNameSynchronizationRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;

/** Official-name synchronization and idempotency coverage. @author Tinashe K */
class OfficialNameSynchronizationServiceTest {

  @Test
  void synchronizesKeycloakLocalProfileAndAuditedEvidenceOnce() throws Exception {
    PlatformUserRepository userRepository = mock(PlatformUserRepository.class);
    OfficialNameSynchronizationRepository synchronizationRepository =
        mock(OfficialNameSynchronizationRepository.class);
    IdentityProvisioningPort identityProvisioningPort = mock(IdentityProvisioningPort.class);
    Instant now = Instant.parse("2026-08-24T10:00:00Z");
    OfficialNameSynchronizationService service =
        new OfficialNameSynchronizationService(
            userRepository,
            synchronizationRepository,
            identityProvisioningPort,
            Clock.fixed(now, ZoneOffset.UTC));
    UUID userId = UUID.randomUUID();
    UUID keycloakUserId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    PlatformUser user =
        new PlatformUser(
            keycloakUserId, "applicant@example.test", "applicant@example.test", "Old Name");
    setId(user, userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(synchronizationRepository.findBySourceRequestIdAndDeletedAtIsNull(requestId))
        .thenReturn(Optional.empty());
    when(synchronizationRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              OfficialNameSynchronization value = invocation.getArgument(0);
              setId(value, UUID.randomUUID());
              return value;
            });

    var result =
        service.synchronize(
            requestId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            "Tinashe",
            "Kudzai",
            "Kadiki",
            "Verified the identity document against the applicant.",
            UUID.randomUUID());

    verify(identityProvisioningPort)
        .updateOfficialName(keycloakUserId, "Tinashe", "Kudzai", "Kadiki");
    assertThat(user.getDisplayName()).isEqualTo("Tinashe Kudzai Kadiki");
    assertThat(result.firstName()).isEqualTo("Tinashe");
    assertThat(result.synchronizedAt()).isEqualTo(now);
  }

  @Test
  void returnsExistingSynchronizationWithoutCallingKeycloakAgain() throws Exception {
    PlatformUserRepository userRepository = mock(PlatformUserRepository.class);
    OfficialNameSynchronizationRepository synchronizationRepository =
        mock(OfficialNameSynchronizationRepository.class);
    IdentityProvisioningPort identityProvisioningPort = mock(IdentityProvisioningPort.class);
    OfficialNameSynchronizationService service =
        new OfficialNameSynchronizationService(
            userRepository, synchronizationRepository, identityProvisioningPort, Clock.systemUTC());
    UUID requestId = UUID.randomUUID();
    PlatformUser user =
        new PlatformUser(UUID.randomUUID(), "user@example.test", "user@example.test", "Old Name");
    setId(user, UUID.randomUUID());
    OfficialNameSynchronization existing =
        new OfficialNameSynchronization(
            requestId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            user,
            "Approved",
            null,
            "Name",
            "Previously approved with identity evidence.",
            Instant.parse("2026-08-24T10:00:00Z"),
            UUID.randomUUID());
    setId(existing, UUID.randomUUID());
    when(synchronizationRepository.findBySourceRequestIdAndDeletedAtIsNull(requestId))
        .thenReturn(Optional.of(existing));

    var result =
        service.synchronize(
            requestId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            user.getId(),
            "Ignored",
            null,
            "Retry",
            "Retry must be idempotent.",
            UUID.randomUUID());

    assertThat(result.firstName()).isEqualTo("Approved");
    verify(identityProvisioningPort, never())
        .updateOfficialName(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  private static void setId(AuditableEntity entity, UUID id) throws Exception {
    Field idField = AuditableEntity.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(entity, id);
  }
}
