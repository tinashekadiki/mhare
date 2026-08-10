package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** @author Tinashe K */
@Service
public class UserAccessProvisioningService {

    private final CoreIdentityService coreIdentityService;
    private final IdentityProvisioningPort identityProvisioningPort;
    private final TransactionTemplate transactionTemplate;

    public UserAccessProvisioningService(
            CoreIdentityService coreIdentityService,
            IdentityProvisioningPort identityProvisioningPort,
            PlatformTransactionManager transactionManager) {
        this.coreIdentityService = coreIdentityService;
        this.identityProvisioningPort = identityProvisioningPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProvisionedUserAccessSummary provisionUserAccess(ProvisionUserAccessCommand command) {
        IdentityProvisioningPort.ProvisionedIdentity provisionedIdentity = identityProvisioningPort.provisionUser(
                command.username(),
                command.email(),
                command.displayName());

        try {
            validateRequestedIdentity(command.keycloakUserId(), provisionedIdentity.keycloakUserId());
            ProvisionedUserAccessSummary localAccess = transactionTemplate.execute(transactionStatus ->
                    coreIdentityService.provisionUserAccess(new ProvisionUserAccessCommand(
                            provisionedIdentity.keycloakUserId(),
                            command.username(),
                            command.email(),
                            command.displayName(),
                            command.phoneNumber(),
                            command.roleAssignments())));
            if (localAccess == null) {
                throw new IllegalStateException("The local user access transaction returned no result.");
            }
            return new ProvisionedUserAccessSummary(
                    localAccess.user(),
                    localAccess.roleAssignments(),
                    provisionedIdentity.created(),
                    provisionedIdentity.temporaryPassword());
        } catch (RuntimeException localProvisioningFailure) {
            compensateNewIdentity(provisionedIdentity, localProvisioningFailure);
            throw localProvisioningFailure;
        }
    }

    private void validateRequestedIdentity(UUID requestedKeycloakUserId, UUID resolvedKeycloakUserId) {
        if (requestedKeycloakUserId != null && !requestedKeycloakUserId.equals(resolvedKeycloakUserId)) {
            throw new IllegalStateException(
                    "The requested Keycloak user does not match the identity resolved from the username and email.");
        }
    }

    private void compensateNewIdentity(
            IdentityProvisioningPort.ProvisionedIdentity provisionedIdentity,
            RuntimeException localProvisioningFailure) {
        if (!provisionedIdentity.created()) {
            return;
        }
        try {
            identityProvisioningPort.deleteUser(provisionedIdentity.keycloakUserId());
        } catch (RuntimeException compensationFailure) {
            localProvisioningFailure.addSuppressed(compensationFailure);
        }
    }
}
