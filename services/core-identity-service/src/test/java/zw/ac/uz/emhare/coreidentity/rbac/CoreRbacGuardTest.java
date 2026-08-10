package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

/** @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class CoreRbacGuardTest {

    @Mock
    private EmhareCurrentUserResolver currentUserResolver;

    @Mock
    private CoreIdentityService coreIdentityService;

    @Mock
    private PlatformUserRepository platformUserRepository;

    @Mock
    private Authentication authentication;

    @Test
    void has_shouldDenyRealmSystemAdministratorUntilLocalProfileIsActive() {
        UUID keycloakUserId = UUID.randomUUID();
        PlatformUser invitedUser = new PlatformUser(
                keycloakUserId,
                "system.admin",
                "system.admin@example.test",
                "System Admin");
        EmhareCurrentUser currentUser = new EmhareCurrentUser(
                keycloakUserId,
                null,
                "system.admin@example.test",
                "system.admin",
                "System Admin",
                Set.of("system-admin"));
        when(currentUserResolver.fromAuthentication(authentication)).thenReturn(Optional.of(currentUser));
        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.of(invitedUser));

        CoreRbacGuard guard = new CoreRbacGuard(
                currentUserResolver,
                coreIdentityService,
                platformUserRepository);

        assertFalse(guard.has(authentication, "CORE_USER_MANAGE"));
    }
}
