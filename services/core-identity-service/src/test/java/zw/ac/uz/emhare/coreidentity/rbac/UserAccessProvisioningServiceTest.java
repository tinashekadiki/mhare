package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class UserAccessProvisioningServiceTest {

    @Mock
    private CoreIdentityService coreIdentityService;

    @Mock
    private IdentityProvisioningPort identityProvisioningPort;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private UserAccessProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        provisioningService = new UserAccessProvisioningService(
                coreIdentityService,
                identityProvisioningPort,
                transactionManager);
    }

    @Test
    void provisionUserAccess_shouldCommitLocalAccessWithResolvedKeycloakIdentity() {
        UUID keycloakUserId = UUID.randomUUID();
        ProvisionUserAccessCommand command = command();
        CoreUserSummary localUser = new CoreUserSummary(
                UUID.randomUUID(), keycloakUserId, command.username(), command.email(), null,
                command.displayName(), "ACTIVE", null);
        ProvisionedUserAccessSummary localAccess = new ProvisionedUserAccessSummary(
                localUser, List.of(), false, null);
        when(identityProvisioningPort.provisionUser(command.username(), command.email(), command.displayName()))
                .thenReturn(new IdentityProvisioningPort.ProvisionedIdentity(
                        keycloakUserId, true, "Temporary-Password-1!"));
        when(coreIdentityService.provisionUserAccess(any(ProvisionUserAccessCommand.class))).thenReturn(localAccess);

        ProvisionedUserAccessSummary result = provisioningService.provisionUserAccess(command);

        assertEquals(keycloakUserId, result.user().keycloakUserId());
        assertEquals("Temporary-Password-1!", result.temporaryPassword());
        assertEquals(true, result.keycloakIdentityCreated());
        ArgumentCaptor<ProvisionUserAccessCommand> localCommand = ArgumentCaptor.forClass(ProvisionUserAccessCommand.class);
        verify(coreIdentityService).provisionUserAccess(localCommand.capture());
        assertEquals(keycloakUserId, localCommand.getValue().keycloakUserId());
        verify(transactionManager).commit(transactionStatus);
        verify(identityProvisioningPort, never()).deleteUser(any(UUID.class));
    }

    @Test
    void provisionUserAccess_shouldDeleteNewKeycloakIdentityWhenDatabaseCommitFails() {
        UUID keycloakUserId = UUID.randomUUID();
        ProvisionUserAccessCommand command = command();
        CoreUserSummary localUser = new CoreUserSummary(
                UUID.randomUUID(), keycloakUserId, command.username(), command.email(), null,
                command.displayName(), "ACTIVE", null);
        when(identityProvisioningPort.provisionUser(command.username(), command.email(), command.displayName()))
                .thenReturn(new IdentityProvisioningPort.ProvisionedIdentity(
                        keycloakUserId, true, "Temporary-Password-1!"));
        when(coreIdentityService.provisionUserAccess(any(ProvisionUserAccessCommand.class)))
                .thenReturn(new ProvisionedUserAccessSummary(localUser, List.of(), false, null));
        doThrow(new UnexpectedRollbackException("Database commit failed"))
                .when(transactionManager).commit(transactionStatus);

        assertThrows(
                UnexpectedRollbackException.class,
                () -> provisioningService.provisionUserAccess(command));

        verify(identityProvisioningPort).deleteUser(keycloakUserId);
    }

    @Test
    void provisionUserAccess_shouldKeepPreExistingKeycloakIdentityWhenLocalTransactionFails() {
        UUID keycloakUserId = UUID.randomUUID();
        ProvisionUserAccessCommand command = command();
        when(identityProvisioningPort.provisionUser(command.username(), command.email(), command.displayName()))
                .thenReturn(new IdentityProvisioningPort.ProvisionedIdentity(keycloakUserId, false, null));
        when(coreIdentityService.provisionUserAccess(any(ProvisionUserAccessCommand.class)))
                .thenThrow(new IllegalStateException("Local role assignment failed"));

        assertThrows(
                IllegalStateException.class,
                () -> provisioningService.provisionUserAccess(command));

        verify(transactionManager).rollback(transactionStatus);
        verify(identityProvisioningPort, never()).deleteUser(any(UUID.class));
    }

    private ProvisionUserAccessCommand command() {
        return new ProvisionUserAccessCommand(
                null,
                "finance.operator",
                "finance.operator@example.test",
                "Finance Operator",
                "+263 77 000 0000",
                List.of(new ProvisionedRoleAssignmentCommand(UUID.randomUUID(), null, null)));
    }
}
