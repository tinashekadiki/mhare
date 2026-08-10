package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;

@Component("coreRbac")
public class CoreRbacGuard {

    private final EmhareCurrentUserResolver currentUserResolver;
    private final CoreIdentityService coreIdentityService;
    private final PlatformUserRepository platformUserRepository;

    public CoreRbacGuard(
            EmhareCurrentUserResolver currentUserResolver,
            CoreIdentityService coreIdentityService,
            PlatformUserRepository platformUserRepository) {
        this.currentUserResolver = currentUserResolver;
        this.coreIdentityService = coreIdentityService;
        this.platformUserRepository = platformUserRepository;
    }

    public boolean has(Authentication authentication, String permissionCode) {
        Optional<EmhareCurrentUser> currentUser = currentUserResolver.fromAuthentication(authentication);
        if (currentUser.isEmpty()) {
            return false;
        }
        return resolveLocalUser(currentUser.get())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> currentUser.get().hasRealmRole("system-admin")
                        || coreIdentityService.can(user.getId(), permissionCode, null).allowed())
                .orElse(false);
    }

    private Optional<PlatformUser> resolveLocalUser(EmhareCurrentUser currentUser) {
        if (currentUser.localUserId() != null) {
            return platformUserRepository.findById(currentUser.localUserId());
        }
        if (currentUser.keycloakUserId() != null) {
            return platformUserRepository.findByKeycloakUserId(currentUser.keycloakUserId());
        }
        if (currentUser.email() != null) {
            return platformUserRepository.findByEmail(currentUser.email());
        }
        return Optional.empty();
    }
}
