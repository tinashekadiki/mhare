package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.InstitutionProfile;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LoginEvent;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupSet;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupValue;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Permission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RolePermission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RoleScope;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserStatus;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.CountryRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.InstitutionProfileRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.LoginEventRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.LookupSetRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.LookupValueRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PermissionRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RolePermissionRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.UserRoleAssignmentRepository;

import zw.ac.uz.emhare.coreidentity.rbac.application.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;

@ExtendWith(MockitoExtension.class)
class CoreIdentityServiceTest {

    @Mock
    private PlatformUserRepository platformUserRepository;

    @Mock
    private InstitutionProfileRepository institutionProfileRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private LookupSetRepository lookupSetRepository;

    @Mock
    private LookupValueRepository lookupValueRepository;

    @Mock
    private LoginEventRepository loginEventRepository;

    private CoreIdentityService coreIdentityService;

    @BeforeEach
    void setUp() {
        coreIdentityService = new CoreIdentityService(
                institutionProfileRepository,
                platformUserRepository,
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                userRoleAssignmentRepository,
                countryRepository,
                lookupSetRepository,
                lookupValueRepository,
                loginEventRepository);
    }

    @Test
    void registerUser_shouldReuseExistingKeycloakUser_whenUserExists() {
        UUID keycloakUserId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(keycloakUserId, "admin@example.test", "admin@example.test", "Admin User");
        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.of(user));

        CoreUserSummary summary = coreIdentityService.registerUser(new RegisterUserCommand(
                keycloakUserId,
                "admin@example.test",
                "admin@example.test",
                "Admin User"));

        assertEquals("admin@example.test", summary.email());
        assertEquals("INVITED", summary.status());
    }

    @Test
    void assignRole_shouldRejectSystemRoleWithAcademicUnitScope() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID academicUnitId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(UUID.randomUUID(), "operator@example.test", "operator@example.test", "Operator");
        Role role = new Role("SYSTEM_ADMIN", "System Admin", RoleScope.SYSTEM, true);
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> coreIdentityService.assignRole(new AssignRoleCommand(userId, roleId, academicUnitId, Instant.now())));

        assertEquals("System roles cannot be scoped to an academic unit.", exception.getMessage());
    }

    @Test
    void assignRole_shouldRejectAcademicUnitRoleWithoutAcademicUnit() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(UUID.randomUUID(), "dean@example.test", "dean@example.test", "Dean User");
        Role role = new Role("FACULTY_DEAN", "Faculty Dean", RoleScope.ACADEMIC_UNIT, true);
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> coreIdentityService.assignRole(new AssignRoleCommand(userId, roleId, null, Instant.now())));

        assertEquals("Academic-unit roles require an academic unit.", exception.getMessage());
    }

    @Test
    void assignRole_shouldRejectDuplicateActiveAssignment() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID academicUnitId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(UUID.randomUUID(), "officer@example.test", "officer@example.test", "Admissions Officer");
        Role role = new Role("ADMISSIONS_OFFICER", "Admissions Officer", RoleScope.ACADEMIC_UNIT, true);
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(user, role, academicUnitId)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coreIdentityService.assignRole(new AssignRoleCommand(userId, roleId, academicUnitId, Instant.now())));

        assertEquals("User already has this active role assignment.", exception.getMessage());
    }

    @Test
    void can_shouldReturnDecisionFromResolvedPermissionCodes() {
        UUID userId = UUID.randomUUID();
        UUID academicUnitId = UUID.randomUUID();
        when(userRoleAssignmentRepository.findPermissionCodes(eq(userId), eq(academicUnitId), any(Instant.class)))
                .thenReturn(List.of("ADMISSIONS_APPLICATION_REVIEW"));

        RbacDecision allowedDecision = coreIdentityService.can(userId, "ADMISSIONS_APPLICATION_REVIEW", academicUnitId);
        RbacDecision deniedDecision = coreIdentityService.can(userId, "FINANCE_PAYMENT_REVERSE", academicUnitId);

        assertTrue(allowedDecision.allowed());
        assertFalse(deniedDecision.allowed());
        verify(userRoleAssignmentRepository, times(2)).findPermissionCodes(eq(userId), eq(academicUnitId), any(Instant.class));
    }

    @Test
    void syncAuthenticatedUser_shouldCreateLocalUserAndAssignApplicantRoleByDefault() {
        UUID keycloakUserId = UUID.randomUUID();
        UUID localUserId = UUID.randomUUID();
        Role applicantRole = new Role("APPLICANT", "Applicant", RoleScope.SYSTEM, true);
        ReflectionTestUtils.setField(applicantRole, "id", UUID.randomUUID());
        PlatformUser[] savedUser = new PlatformUser[1];
        UserRoleAssignment[] savedAssignment = new UserRoleAssignment[1];

        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.empty());
        when(platformUserRepository.findByEmail("applicant@example.test")).thenReturn(Optional.empty());
        when(platformUserRepository.save(any(PlatformUser.class))).thenAnswer(invocation -> {
            savedUser[0] = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedUser[0], "id", localUserId);
            return savedUser[0];
        });
        when(roleRepository.findByCode("APPLICANT")).thenReturn(Optional.of(applicantRole));
        when(userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(any(PlatformUser.class), eq(applicantRole), eq(null)))
                .thenReturn(false);
        when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> {
            savedAssignment[0] = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedAssignment[0], "id", UUID.randomUUID());
            return savedAssignment[0];
        });
        when(platformUserRepository.findById(localUserId)).thenAnswer(invocation -> Optional.of(savedUser[0]));
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(localUserId))
                .thenAnswer(invocation -> savedAssignment[0] == null ? List.of() : List.of(savedAssignment[0]));

        CurrentUserProfile profile = coreIdentityService.syncAuthenticatedUser(new EmhareCurrentUser(
                keycloakUserId,
                null,
                "applicant@example.test",
                "applicant",
                "Applicant User",
                Set.of("applicant")));

        assertEquals(localUserId, profile.user().id());
        assertEquals("ACTIVE", profile.user().status());
        assertEquals(Set.of("applicant"), profile.realmRoles());
        assertTrue(profile.effectivePermissionCodes().isEmpty());
        assertFalse(profile.operationalAccess());
        verify(platformUserRepository).acquireIdentitySynchronizationLock(keycloakUserId);
        verify(userRoleAssignmentRepository).save(any(UserRoleAssignment.class));
        verify(loginEventRepository).save(any(LoginEvent.class));
    }

    @Test
    void syncAuthenticatedUser_shouldRecordOneLoginEventPerIdentitySession() {
        UUID keycloakUserId = UUID.randomUUID();
        UUID localUserId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(
                keycloakUserId,
                "core.operator",
                "core.operator@example.test",
                "Core Operator");
        ReflectionTestUtils.setField(user, "id", localUserId);
        user.activate();
        EmhareCurrentUser currentUser = new EmhareCurrentUser(
                keycloakUserId,
                localUserId,
                "core.operator@example.test",
                "core.operator",
                "Core Operator",
                Set.of());
        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.of(user));
        when(loginEventRepository.existsByKeycloakUserIdAndIdentitySessionIdAndDeletedAtIsNull(
                keycloakUserId, "session-123")).thenReturn(false, true);
        when(platformUserRepository.findById(localUserId)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(localUserId)).thenReturn(List.of());
        when(userRoleAssignmentRepository.findPermissionCodesAcrossScopes(eq(localUserId), any(Instant.class)))
                .thenReturn(List.of());

        coreIdentityService.syncAuthenticatedUser(currentUser, null, null, "session-123");
        coreIdentityService.syncAuthenticatedUser(currentUser, null, null, "session-123");
        coreIdentityService.syncAuthenticatedUser(currentUser, null, null, " ");

        org.mockito.ArgumentCaptor<LoginEvent> loginEventCaptor = org.mockito.ArgumentCaptor.forClass(LoginEvent.class);
        verify(loginEventRepository, times(2)).save(loginEventCaptor.capture());
        assertEquals(" ", loginEventCaptor.getAllValues().get(1).getIdentitySessionId());
    }

    @Test
    void currentUserProfile_shouldExposeActiveOperationalRolePermissions() {
        UUID userId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(
                UUID.randomUUID(),
                "operator@example.test",
                "operator@example.test",
                "Core Operator");
        ReflectionTestUtils.setField(user, "id", userId);
        user.activate();
        Role coreOperatorRole = new Role("CORE_OPERATOR", "Core Operator", RoleScope.SYSTEM, false);
        ReflectionTestUtils.setField(coreOperatorRole, "id", UUID.randomUUID());
        UserRoleAssignment assignment = new UserRoleAssignment(user, coreOperatorRole, null, Instant.now().minusSeconds(60));
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());

        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(assignment));
        when(userRoleAssignmentRepository.findPermissionCodesAcrossScopes(eq(userId), any(Instant.class)))
                .thenReturn(List.of("CORE_USER_MANAGE"));

        CurrentUserProfile profile = coreIdentityService.currentUserProfile(userId);

        assertTrue(profile.operationalAccess());
        assertEquals(Set.of("CORE_USER_MANAGE"), profile.effectivePermissionCodes());
        assertEquals("CORE_OPERATOR", profile.roleAssignments().getFirst().roleCode());
    }

    @Test
    void syncAuthenticatedUser_shouldPreserveProvisionedLocalRoleWithoutAddingApplicantRole() {
        UUID keycloakUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(
                null,
                "finance.operator",
                "finance.operator@example.test",
                "Finance Operator");
        ReflectionTestUtils.setField(user, "id", userId);
        user.activate();
        Role financeRole = new Role("FINANCE_OFFICER", "Finance Officer", RoleScope.SYSTEM, true);
        ReflectionTestUtils.setField(financeRole, "id", UUID.randomUUID());
        UserRoleAssignment financeAssignment = new UserRoleAssignment(
                user,
                financeRole,
                null,
                Instant.now().minusSeconds(60));
        ReflectionTestUtils.setField(financeAssignment, "id", UUID.randomUUID());

        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.empty());
        when(platformUserRepository.findByEmail("finance.operator@example.test")).thenReturn(Optional.of(user));
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(List.of(financeAssignment));
        when(userRoleAssignmentRepository.findPermissionCodesAcrossScopes(eq(userId), any(Instant.class)))
                .thenReturn(List.of("ADMISSIONS_PAYMENT_OVERRIDE"));

        CurrentUserProfile profile = coreIdentityService.syncAuthenticatedUser(new EmhareCurrentUser(
                keycloakUserId,
                null,
                "finance.operator@example.test",
                "finance.operator",
                "Finance Operator",
                Set.of()));

        assertEquals("FINANCE_OFFICER", profile.roleAssignments().getFirst().roleCode());
        assertEquals(1, profile.roleAssignments().size());
        assertTrue(profile.operationalAccess());
        verify(roleRepository, never()).findByCode("APPLICANT");
    }

    @Test
    void provisionUserAccess_shouldAssignRoleBeforeActivatingProfile() {
        UUID userId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(
                null,
                "finance.operator",
                "finance.operator@example.test",
                "Finance Operator");
        ReflectionTestUtils.setField(user, "id", userId);
        Role role = new Role("FINANCE_OFFICER", "Finance Officer", RoleScope.SYSTEM, true);
        ReflectionTestUtils.setField(role, "id", roleId);
        Permission permission = new Permission(
                "FINANCE_PAYMENT_VIEW",
                "View payments",
                PermissionCategory.FINANCE,
                "View captured payments.");
        RolePermission rolePermission = new RolePermission(role, permission);
        UserRoleAssignment assignment = new UserRoleAssignment(user, role, null, Instant.now());
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());

        when(platformUserRepository.findByEmail("finance.operator@example.test"))
                .thenReturn(Optional.of(user));
        when(platformUserRepository.findByKeycloakUserId(keycloakUserId)).thenReturn(Optional.empty());
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleIdAndDeletedAtIsNull(roleId))
                .thenReturn(List.of(rolePermission));
        when(userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
                user, role, null)).thenReturn(false);
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(List.of(assignment));

        ProvisionedUserAccessSummary summary = coreIdentityService.provisionUserAccess(
                new ProvisionUserAccessCommand(
                        keycloakUserId,
                        "finance.operator",
                        "finance.operator@example.test",
                        "Finance Operator",
                        "+263 77 000 0000",
                        List.of(new ProvisionedRoleAssignmentCommand(roleId, null, null))));

        assertEquals("ACTIVE", summary.user().status());
        assertEquals(keycloakUserId, user.getKeycloakUserId());
        assertEquals("FINANCE_OFFICER", summary.roleAssignments().getFirst().roleCode());
        verify(userRoleAssignmentRepository).save(any(UserRoleAssignment.class));
        verify(platformUserRepository).save(user);
    }

    @Test
    void provisionUserAccess_shouldRejectProfileWithoutRoleAssignments() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> coreIdentityService.provisionUserAccess(new ProvisionUserAccessCommand(
                        null,
                        "incomplete.user",
                        "incomplete.user@example.test",
                        "Incomplete User",
                        null,
                        List.of())));

        assertEquals(
                "At least one role assignment is required to complete a user access profile.",
                exception.getMessage());
    }

    @Test
    void updateUser_shouldRejectActivationWithoutUsableRoleAssignment() {
        UUID userId = UUID.randomUUID();
        PlatformUser user = new PlatformUser(
                null,
                "incomplete.user",
                "incomplete.user@example.test",
                "Incomplete User");
        ReflectionTestUtils.setField(user, "id", userId);
        when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> coreIdentityService.updateUser(
                        userId,
                        new UpdateUserCommand("Incomplete User", null, UserStatus.ACTIVE)));

        assertEquals(
                "Assign a role with usable access before activating this user.",
                exception.getMessage());
        assertEquals(UserStatus.INVITED, user.getStatus());
    }

    @Test
    void upsertInstitutionProfile_shouldCreateProfileWhenMissing() {
        UUID profileId = UUID.randomUUID();
        when(institutionProfileRepository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(institutionProfileRepository.save(any(InstitutionProfile.class))).thenAnswer(invocation -> {
            InstitutionProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", profileId);
            return profile;
        });

        InstitutionProfileSummary summary = coreIdentityService.upsertInstitutionProfile(new UpsertInstitutionProfileCommand(
                "uz",
                "University of Zimbabwe",
                "University of Zimbabwe",
                "USD",
                "ZW",
                "Africa/Harare",
                "{}",
                "{}",
                "UZ"));

        assertEquals(profileId, summary.id());
        assertEquals("UZ", summary.code());
        assertEquals("USD", summary.defaultCurrencyCode());
    }

    @Test
    void upsertLookupValue_shouldReuseExistingValueWithinSet() {
        UUID lookupSetId = UUID.randomUUID();
        UUID lookupValueId = UUID.randomUUID();
        LookupSet lookupSet = new LookupSet("DOCUMENT_TYPES", "Document types", null);
        ReflectionTestUtils.setField(lookupSet, "id", lookupSetId);
        LookupValue lookupValue = new LookupValue(lookupSet, "PASSPORT", "Passport", 20, true);
        ReflectionTestUtils.setField(lookupValue, "id", lookupValueId);

        when(lookupSetRepository.findById(lookupSetId)).thenReturn(Optional.of(lookupSet));
        when(lookupValueRepository.findByLookupSetAndCode(lookupSet, "PASSPORT")).thenReturn(Optional.of(lookupValue));
        when(lookupValueRepository.save(lookupValue)).thenReturn(lookupValue);

        LookupValueSummary summary = coreIdentityService.upsertLookupValue(
                lookupSetId,
                new UpsertLookupValueCommand("passport", "Passport or travel document", 30, false));

        assertEquals(lookupValueId, summary.id());
        assertEquals("Passport or travel document", summary.name());
        assertEquals(30, summary.sortOrder());
        assertFalse(summary.active());
    }
}
