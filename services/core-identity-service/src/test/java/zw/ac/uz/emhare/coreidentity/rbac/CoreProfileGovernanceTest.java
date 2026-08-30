package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class CoreProfileGovernanceTest {
  private final InstitutionProfileRepository institution = mock(InstitutionProfileRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final PermissionRepository permissions = mock(PermissionRepository.class);
  private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
  private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
  private final CountryRepository countries = mock(CountryRepository.class);
  private final LookupSetRepository sets = mock(LookupSetRepository.class);
  private final LookupValueRepository values = mock(LookupValueRepository.class);
  private final LoginEventRepository logins = mock(LoginEventRepository.class);
  private final CoreIdentityService service =
      new CoreIdentityService(
          institution,
          users,
          roles,
          permissions,
          grants,
          assignments,
          countries,
          sets,
          values,
          logins);
  private PlatformUser user;
  private Role studentRole;

  @BeforeEach
  void setup() {
    user = new PlatformUser(UUID.randomUUID(), "student", "student@example.test", "Student");
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    studentRole = new Role("STUDENT", "Student", RoleScope.SYSTEM, true);
    ReflectionTestUtils.setField(studentRole, "id", UUID.randomUUID());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NULL_ROLES", "NO_IDENTITY", "DUPLICATE", "FUTURE", "UNGRANTED_ROLE"})
  void provisionAccess_shouldRejectIncompleteOrContradictoryAssignments(String scenario) {
    Role role =
        scenario.equals("UNGRANTED_ROLE")
            ? new Role("OPERATOR", "Operator", RoleScope.SYSTEM, false)
            : studentRole;
    if (role != studentRole) ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
    var assignment =
        new ProvisionedRoleAssignmentCommand(
            role.getId(),
            null,
            scenario.equals("FUTURE") ? Instant.now().plusSeconds(86400) : null);
    List<ProvisionedRoleAssignmentCommand> requested =
        scenario.equals("NULL_ROLES")
            ? null
            : scenario.equals("DUPLICATE") ? List.of(assignment, assignment) : List.of(assignment);
    when(users.findByKeycloakUserId(user.getKeycloakUserId())).thenReturn(Optional.of(user));
    when(roles.findById(role.getId())).thenReturn(Optional.of(role));
    var command =
        new ProvisionUserAccessCommand(
            scenario.equals("NO_IDENTITY") ? null : user.getKeycloakUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            null,
            requested);
    RuntimeException error =
        assertThrows(RuntimeException.class, () -> service.provisionUserAccess(command));
    String expected =
        switch (scenario) {
          case "NULL_ROLES" -> "At least one role assignment";
          case "NO_IDENTITY" -> "Keycloak user id is required";
          case "DUPLICATE" -> "same role and scope";
          case "FUTURE" -> "must start immediately";
          default -> "has no permissions";
        };
    assertTrue(error.getMessage().contains(expected), error::getMessage);
    assertEquals(UserStatus.INVITED, user.getStatus());
  }

  @Test
  void provisionAccess_shouldReuseExistingIdentityAndAssignmentBeforeActivating() {
    var assignment =
        new UserRoleAssignment(user, studentRole, null, Instant.now().minusSeconds(60));
    ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
    when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    when(roles.findById(studentRole.getId())).thenReturn(Optional.of(studentRole));
    when(assignments.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(user, studentRole, null))
        .thenReturn(true);
    when(assignments.findByUserIdAndDeletedAtIsNull(user.getId())).thenReturn(List.of(assignment));
    var result =
        service.provisionUserAccess(
            new ProvisionUserAccessCommand(
                user.getKeycloakUserId(),
                "student",
                user.getEmail(),
                "Official Name",
                "12345",
                List.of(
                    new ProvisionedRoleAssignmentCommand(
                        studentRole.getId(), null, Instant.now().minusSeconds(60)))));
    assertEquals("ACTIVE", result.user().status());
    assertEquals("Official Name", result.user().displayName());
    assertEquals(assignment.getId(), result.roleAssignments().getFirst().id());
    verify(assignments, never()).save(any());
    verify(users).save(user);
    verifyNoInteractions(grants);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DELETED", "ENDED", "DUPLICATE", "WRONG_OWNER"})
  void reassignAcademicUnit_shouldRejectInactiveDuplicateOrForeignAssignment(String scenario) {
    Role academic =
        new Role("ACADEMIC_UNIT_STAFF", "Academic staff", RoleScope.ACADEMIC_UNIT, true);
    ReflectionTestUtils.setField(academic, "id", UUID.randomUUID());
    UUID originalUnit = UUID.randomUUID();
    UUID requestedUnit = UUID.randomUUID();
    UserRoleAssignment assignment =
        new UserRoleAssignment(user, academic, originalUnit, Instant.now().minusSeconds(60));
    ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
    if (scenario.equals("DELETED")) assignment.markDeleted(UUID.randomUUID());
    if (scenario.equals("ENDED")) assignment.end(Instant.now());
    when(assignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    if (scenario.equals("DUPLICATE"))
      when(assignments.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
              user, academic, requestedUnit))
          .thenReturn(true);
    RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () ->
                service.updateRoleAssignmentAcademicUnit(
                    scenario.equals("WRONG_OWNER") ? UUID.randomUUID() : user.getId(),
                    assignment.getId(),
                    requestedUnit));
    assertEquals(
        scenario.equals("WRONG_OWNER")
            ? "Role assignment does not belong to the user."
            : scenario.equals("DUPLICATE")
                ? "User already has this active role assignment."
                : "Only an active role assignment can be updated.",
        failure.getMessage());
    assertEquals(originalUnit, assignment.getAcademicUnitId());
    verify(assignments, never()).save(any());
  }

  @Test
  void expireAssignment_shouldRejectForeignUserWithoutChangingLifetime() {
    UserRoleAssignment assignment = new UserRoleAssignment(user, studentRole, null, Instant.now());
    ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
    when(assignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.expireRoleAssignment(UUID.randomUUID(), assignment.getId(), UUID.randomUUID()));
    assertNull(assignment.getEndsAt());
    assertFalse(assignment.isDeleted());
  }

  @ParameterizedTest
  @ValueSource(strings = {"FUTURE", "EXPIRED", "CURRENT", "SELF_SERVICE", "INVITED"})
  void profile_shouldExposeOnlyCurrentAssignmentsAndRequireOperationalPermission(String scenario) {
    Instant now = Instant.now();
    Role role =
        scenario.equals("SELF_SERVICE")
            ? studentRole
            : new Role("OPERATOR", "Operator", RoleScope.SYSTEM, false);
    ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
    UserRoleAssignment assignment =
        new UserRoleAssignment(
            user,
            role,
            null,
            scenario.equals("FUTURE") ? now.plusSeconds(3600) : now.minusSeconds(3600));
    ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
    if (scenario.equals("EXPIRED")) assignment.end(now.minusSeconds(1));
    if (scenario.equals("CURRENT")) assignment.end(now.plusSeconds(3600));
    if (!scenario.equals("INVITED")) user.activate();
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(assignments.findByUserIdAndDeletedAtIsNull(user.getId())).thenReturn(List.of(assignment));
    when(assignments.findPermissionCodesAcrossScopes(eq(user.getId()), any()))
        .thenReturn(List.of("CORE_USER_MANAGE"));
    CurrentUserProfile result = service.currentUserProfile(user.getId());
    assertEquals(
        scenario.equals("FUTURE") || scenario.equals("EXPIRED") ? 0 : 1,
        result.roleAssignments().size());
    assertEquals(scenario.equals("CURRENT"), result.operationalAccess());
    if (scenario.equals("INVITED")) assertTrue(result.effectivePermissionCodes().isEmpty());
  }

  @Test
  void updateUser_shouldPermitSelfServiceActivationAndNonActiveAdministrativeStatus() {
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(assignments.findByUserIdAndDeletedAtIsNull(user.getId()))
        .thenReturn(
            List.of(
                new UserRoleAssignment(user, studentRole, null, Instant.now().minusSeconds(1))));
    assertEquals(
        "ACTIVE",
        service
            .updateUser(user.getId(), new UpdateUserCommand("Student", "123", UserStatus.ACTIVE))
            .status());
    assertEquals(
        "LOCKED",
        service
            .updateUser(user.getId(), new UpdateUserCommand("Student", "123", UserStatus.LOCKED))
            .status());
    verify(assignments, never()).findPermissionCodesAcrossScopes(any(), any());
  }

  @Test
  void registerUser_shouldReuseEmailIdentityWithoutCreatingAnotherUser() {
    when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    assertEquals(
        user.getId(),
        service
            .registerUser(new RegisterUserCommand(null, "student", user.getEmail(), "Student"))
            .id());
    verify(users, never()).save(any());
  }

  @Test
  void loginAudit_shouldProjectLinkedAndUnlinkedAttemptsWithoutInventingLocalIdentity() {
    UUID identityId = user.getKeycloakUserId();
    LoginEvent linked =
        new LoginEvent(
            user,
            identityId,
            "student",
            user.getEmail(),
            "127.0.0.1",
            "browser",
            "session",
            LoginOutcome.SUCCESS);
    LoginEvent unlinked =
        new LoginEvent(
            null,
            identityId,
            "student",
            user.getEmail(),
            "127.0.0.2",
            "other",
            null,
            LoginOutcome.SUCCESS);
    when(logins.findTop100ByDeletedAtIsNullOrderByOccurredAtDesc())
        .thenReturn(List.of(linked, unlinked));
    var result = service.listRecentLoginEvents();
    assertEquals(user.getId(), result.getFirst().userId());
    assertNull(result.get(1).userId());
    assertEquals(identityId, result.get(1).keycloakUserId());
    assertEquals("127.0.0.1", result.getFirst().ipAddress());
    assertEquals("browser", result.getFirst().userAgent());
    assertEquals(linked.getOccurredAt(), result.getFirst().occurredAt());
    assertEquals("session", linked.getIdentitySessionId());
  }
}
