package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;

/**
 * @author Tinashe K
 */
class CoreAccessBoundaryTest {
  private final EmhareCurrentUserResolver resolver = mock(EmhareCurrentUserResolver.class);
  private final CoreIdentityService permissions = mock(CoreIdentityService.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final Authentication authentication = mock(Authentication.class);
  private final CoreRbacGuard guard = new CoreRbacGuard(resolver, permissions, users);

  @Test
  void guard_shouldDenyUnresolvedAuthenticationWithoutRepositoryAccess() {
    assertFalse(guard.has(authentication, "CORE_USER_MANAGE"));
    verifyNoInteractions(users, permissions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"LOCAL", "KEYCLOAK", "EMAIL", "NONE"})
  void guard_shouldResolveOnlyTheMostSpecificIdentityAndApplyLocalPermission(String lookup) {
    PlatformUser user = user();
    UUID localId = lookup.equals("LOCAL") ? user.getId() : null;
    UUID keycloakId =
        lookup.equals("LOCAL") || lookup.equals("KEYCLOAK") ? user.getKeycloakUserId() : null;
    String email = lookup.equals("NONE") ? null : user.getEmail();
    when(resolver.fromAuthentication(authentication))
        .thenReturn(
            Optional.of(
                new EmhareCurrentUser(
                    keycloakId, localId, email, "reviewer", "Reviewer", Set.of())));
    switch (lookup) {
      case "LOCAL" -> when(users.findById(localId)).thenReturn(Optional.of(user));
      case "KEYCLOAK" -> when(users.findByKeycloakUserId(keycloakId)).thenReturn(Optional.of(user));
      case "EMAIL" -> when(users.findByEmail(email)).thenReturn(Optional.of(user));
      default -> {}
    }
    if (!lookup.equals("NONE"))
      when(permissions.can(user.getId(), "CORE_USER_MANAGE", null))
          .thenReturn(new RbacDecision(user.getId(), null, "CORE_USER_MANAGE", true));
    assertEquals(!lookup.equals("NONE"), guard.has(authentication, "CORE_USER_MANAGE"));
    if (lookup.equals("LOCAL")) {
      verify(users, never()).findByKeycloakUserId(any());
      verify(users, never()).findByEmail(any());
    }
    if (lookup.equals("NONE")) verifyNoInteractions(users, permissions);
  }

  @ParameterizedTest
  @CsvSource({
    "ACTIVE,false,false",
    "ACTIVE,true,true",
    "INVITED,true,false",
    "LOCKED,true,false",
    "DISABLED,true,false"
  })
  void guard_shouldRequireActiveLocalStatusEvenForRealmAdministrator(
      UserStatus status, boolean administrator, boolean allowed) {
    PlatformUser user = user();
    user.updateProfile("Reviewer", null, status);
    when(resolver.fromAuthentication(authentication))
        .thenReturn(
            Optional.of(
                new EmhareCurrentUser(
                    user.getKeycloakUserId(),
                    user.getId(),
                    user.getEmail(),
                    "reviewer",
                    "Reviewer",
                    administrator ? Set.of("system-admin") : Set.of())));
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    if (status == UserStatus.ACTIVE && !administrator)
      when(permissions.can(any(), any(), isNull()))
          .thenReturn(new RbacDecision(user.getId(), null, "CORE_USER_MANAGE", false));
    assertEquals(allowed, guard.has(authentication, "CORE_USER_MANAGE"));
    if (administrator || status != UserStatus.ACTIVE) verifyNoInteractions(permissions);
  }

  @Test
  void guard_shouldNotFallBackToEmailWhenExplicitLocalIdentityIsMissing() {
    when(resolver.fromAuthentication(authentication))
        .thenReturn(
            Optional.of(
                new EmhareCurrentUser(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "reviewer@example.test",
                    "reviewer",
                    "Reviewer",
                    Set.of("system-admin"))));
    assertFalse(guard.has(authentication, "CORE_USER_MANAGE"));
    verify(users, never()).findByEmail(any());
    verify(users, never()).findByKeycloakUserId(any());
    verifyNoInteractions(permissions);
  }

  @ParameterizedTest
  @CsvSource({"0,0,true", "1,0,false", "-1,1,true", "-1,-1,false"})
  void roleAssignment_shouldRespectInclusiveStartAndExclusiveEnd(
      long startOffset, long endOffset, boolean active) {
    Instant asOf = Instant.parse("2026-08-30T10:00:00Z");
    UserRoleAssignment assignment =
        new UserRoleAssignment(
            user(),
            new Role("STUDENT", "Student", RoleScope.SYSTEM, true),
            null,
            asOf.plusSeconds(startOffset));
    if (endOffset != 0) assignment.end(asOf.plusSeconds(endOffset));
    assertEquals(active, assignment.isActiveAt(asOf));
    assignment.end(asOf);
    assertFalse(
        assignment.isActiveAt(asOf), "Access expires exactly at endsAt, not one instant later");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void officialNames_shouldRejectMissingFirstOrLastName(String missing) {
    PlatformUser user = user();
    assertEquals(
        "First name is required.",
        assertThrows(
                IllegalArgumentException.class,
                () -> user.synchronizeOfficialName(missing, null, "Moyo"))
            .getMessage());
    assertEquals(
        "Last name is required.",
        assertThrows(
                IllegalArgumentException.class,
                () -> user.synchronizeOfficialName("Tariro", null, missing))
            .getMessage());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " Grace "})
  void officialNames_shouldNormalizeOptionalMiddleNameAndPreserveApprovedDisplayOnLogin(
      String middle) {
    PlatformUser user = user();
    user.synchronizeOfficialName(" Tariro ", middle, " Moyo ");
    user.syncFromIdentityProvider(
        user.getKeycloakUserId(), "reviewer", user.getEmail(), "Outdated identity name");
    assertEquals(
        middle != null && !middle.isBlank() ? "Tariro Grace Moyo" : "Tariro Moyo",
        user.getDisplayName());
    assertEquals("Tariro", user.getFirstName());
    assertEquals("Moyo", user.getLastName());
    assertNotNull(user.getLastLoginAt());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void identityLink_shouldUseEmailWhenPreferredUsernameAndDisplayNameAreAbsent(String absent) {
    PlatformUser user = new PlatformUser(null, "old", "old@example.test", "Old");
    UUID identity = UUID.randomUUID();
    user.linkIdentityProvider(identity, absent, "new@example.test", absent);
    assertEquals("new@example.test", user.getUsername());
    assertEquals("new@example.test", user.getDisplayName());
    assertEquals(identity, user.getKeycloakUserId());
    user.linkIdentityProvider(identity, "account", absent, "Display");
    assertEquals("account", user.getEmail());
    assertEquals("Display", user.getDisplayName());
  }

  @Test
  void identityLink_shouldRejectMissingOrDifferentIdentityWithoutRelinking() {
    PlatformUser user = user();
    UUID original = user.getKeycloakUserId();
    assertThrows(
        IllegalArgumentException.class, () -> user.linkIdentityProvider(null, "x", "x", "x"));
    assertThrows(
        IllegalStateException.class,
        () -> user.linkIdentityProvider(UUID.randomUUID(), "x", "x", "x"));
    assertEquals(original, user.getKeycloakUserId());
    assertEquals("reviewer@example.test", user.getEmail());
  }

  private PlatformUser user() {
    PlatformUser user =
        new PlatformUser(UUID.randomUUID(), "reviewer", "reviewer@example.test", "Reviewer");
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    user.activate();
    return user;
  }
}
