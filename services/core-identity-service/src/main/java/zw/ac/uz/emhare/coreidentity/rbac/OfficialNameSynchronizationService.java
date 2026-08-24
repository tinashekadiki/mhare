package zw.ac.uz.emhare.coreidentity.rbac;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.OfficialNameSynchronization;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.OfficialNameSynchronizationRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;

/**
 * Idempotently applies an Admissions-approved official name to Core and Keycloak. @author Tinashe K
 */
@Service
public class OfficialNameSynchronizationService {

  private final PlatformUserRepository userRepository;
  private final OfficialNameSynchronizationRepository synchronizationRepository;
  private final IdentityProvisioningPort identityProvisioningPort;
  private final Clock clock;

  public OfficialNameSynchronizationService(
      PlatformUserRepository userRepository,
      OfficialNameSynchronizationRepository synchronizationRepository,
      IdentityProvisioningPort identityProvisioningPort,
      Clock clock) {
    this.userRepository = userRepository;
    this.synchronizationRepository = synchronizationRepository;
    this.identityProvisioningPort = identityProvisioningPort;
    this.clock = clock;
  }

  @Transactional
  public OfficialNameSynchronizationSummary synchronize(
      UUID sourceRequestId,
      UUID sourceApplicationId,
      UUID sourceDocumentId,
      UUID userId,
      String firstName,
      String middleNames,
      String lastName,
      String approvalReason,
      UUID actorUserId) {
    return synchronizationRepository
        .findBySourceRequestIdAndDeletedAtIsNull(sourceRequestId)
        .map(OfficialNameSynchronizationSummary::from)
        .orElseGet(
            () -> {
              PlatformUser user =
                  userRepository
                      .findById(userId)
                      .filter(value -> !value.isDeleted())
                      .orElseThrow(() -> new IllegalArgumentException("Core user was not found."));
              identityProvisioningPort.updateOfficialName(
                  user.getKeycloakUserId(), firstName, middleNames, lastName);
              Instant synchronizedAt = clock.instant();
              OfficialNameSynchronization synchronization =
                  new OfficialNameSynchronization(
                      sourceRequestId,
                      sourceApplicationId,
                      sourceDocumentId,
                      user,
                      firstName,
                      middleNames,
                      lastName,
                      approvalReason,
                      synchronizedAt,
                      actorUserId);
              user.synchronizeOfficialName(firstName, middleNames, lastName);
              userRepository.save(user);
              return OfficialNameSynchronizationSummary.from(
                  synchronizationRepository.saveAndFlush(synchronization));
            });
  }

  public record OfficialNameSynchronizationSummary(
      UUID id,
      UUID sourceRequestId,
      UUID userId,
      String firstName,
      String middleNames,
      String lastName,
      Instant synchronizedAt) {
    static OfficialNameSynchronizationSummary from(OfficialNameSynchronization value) {
      return new OfficialNameSynchronizationSummary(
          value.getId(),
          value.getSourceRequestId(),
          value.getUser().getId(),
          value.getApprovedFirstName(),
          value.getApprovedMiddleNames(),
          value.getApprovedLastName(),
          value.getSynchronizedAt());
    }
  }
}
