package zw.ac.uz.emhare.coreidentity.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.api.model.*;
import zw.ac.uz.emhare.coreidentity.audit.CoreAuditService;
import zw.ac.uz.emhare.coreidentity.audit.CoreOperationalReport;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RoleScope;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserStatus;

/** Release 1 controller audit-boundary regressions. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class CoreIdentityControllerTest {

    @Mock private CoreIdentityService coreIdentityService;
    @Mock private UserAccessProvisioningService provisioningService;
    @Mock private EmhareCurrentUserResolver currentUserResolver;
    @Mock private CoreAuditService auditService;
    @Mock private Authentication authentication;

    private CoreIdentityController controller;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(currentUserResolver.fromAuthentication(authentication)).thenReturn(Optional.of(
                new EmhareCurrentUser(UUID.randomUUID(), actorId, "admin@uz.ac.zw", "admin", "Admin", Set.of())));
        controller = new CoreIdentityController(coreIdentityService, provisioningService, currentUserResolver, auditService);
    }

    @Test
    void auditReadsAndSessionSync_shouldDelegateToReleaseOneServices() {
        when(coreIdentityService.coreStatistics()).thenReturn(Map.of("userCount", 3L));
        CoreOperationalReport report = new CoreOperationalReport(Instant.now(), Map.of("userCount", 3L), 2, 4);
        when(auditService.operationalReport(any())).thenReturn(report);
        when(auditService.recentEvents()).thenReturn(List.of());

        assertThat(controller.auditEvents()).isEmpty();
        assertThat(controller.operationalReport()).isSameAs(report);
        controller.me(authentication);

        verify(coreIdentityService).syncAuthenticatedUser(any(), any(), any(), any());
    }

    @Test
    void me_shouldUseSidAndFallBackToJtiForIdentitySessionTracking() {
        JwtAuthenticationToken sessionAuthentication = jwtAuthentication(Map.of("sid", "session-1", "jti", "token-1"));
        JwtAuthenticationToken tokenAuthentication = jwtAuthentication(Map.of("jti", "token-2"));
        JwtAuthenticationToken blankSessionAuthentication = jwtAuthentication(Map.of("sid", " ", "jti", "token-3"));
        EmhareCurrentUser currentUser = new EmhareCurrentUser(UUID.randomUUID(), actorId, "admin@uz.ac.zw", "admin", "Admin", Set.of());
        when(currentUserResolver.fromAuthentication(sessionAuthentication)).thenReturn(Optional.of(currentUser));
        when(currentUserResolver.fromAuthentication(tokenAuthentication)).thenReturn(Optional.of(currentUser));
        when(currentUserResolver.fromAuthentication(blankSessionAuthentication)).thenReturn(Optional.of(currentUser));

        controller.me(sessionAuthentication);
        controller.me(tokenAuthentication);
        controller.me(blankSessionAuthentication);

        verify(coreIdentityService).syncAuthenticatedUser(currentUser, null, null, "session-1");
        verify(coreIdentityService).syncAuthenticatedUser(currentUser, null, null, "token-2");
        verify(coreIdentityService).syncAuthenticatedUser(currentUser, null, null, "token-3");
    }

    @Test
    void governedMutations_shouldRecordReadableAuditEvidence() {
        UUID recordId = UUID.randomUUID();
        CoreUserSummary user = new CoreUserSummary(recordId, UUID.randomUUID(), "staff", "staff@uz.ac.zw", null, "Staff", "ACTIVE", null);
        RoleSummary role = new RoleSummary(recordId, "REGISTRY", "Registry", RoleScope.SYSTEM, false);
        PermissionSummary permission = new PermissionSummary(recordId, "CORE_READ", "Read", PermissionCategory.CORE, "Read Core");
        RolePermissionSummary grant = new RolePermissionSummary(recordId, recordId, recordId, "CORE_READ", "Read", PermissionCategory.CORE);
        UserRoleAssignmentSummary assignment = new UserRoleAssignmentSummary(recordId, recordId, "REGISTRY", "Registry", null, Instant.now(), null);
        CountrySummary country = new CountrySummary(recordId, "ZW", "ZWE", "Zimbabwe", "Zimbabwean");
        LookupSetSummary lookupSet = new LookupSetSummary(recordId, "GENDER", "Gender", "Values");
        LookupValueSummary lookupValue = new LookupValueSummary(recordId, recordId, "GENDER", "F", "Female", 1, true);
        InstitutionProfileSummary profile = new InstitutionProfileSummary(recordId, "UZ", "University of Zimbabwe", "University of Zimbabwe", "USD", "ZW", "Africa/Harare", "{}", "{}", "UZ");

        when(coreIdentityService.institutionProfile()).thenReturn(profile);
        when(coreIdentityService.upsertInstitutionProfile(any())).thenReturn(profile);
        when(coreIdentityService.registerUser(any())).thenReturn(user);
        when(coreIdentityService.listUsers()).thenReturn(List.of(user));
        when(coreIdentityService.updateUser(any(), any())).thenReturn(user);
        when(coreIdentityService.createRole(any())).thenReturn(role);
        when(coreIdentityService.listRoles()).thenReturn(List.of(role));
        when(coreIdentityService.updateRole(any(), any())).thenReturn(role);
        when(coreIdentityService.createPermission(any())).thenReturn(permission);
        when(coreIdentityService.listPermissions()).thenReturn(List.of(permission));
        when(coreIdentityService.updatePermission(any(), any())).thenReturn(permission);
        when(coreIdentityService.grantPermissionToRole(any(), any())).thenReturn(grant);
        when(coreIdentityService.assignRole(any())).thenReturn(assignment);
        when(coreIdentityService.upsertCountry(any())).thenReturn(country);
        when(coreIdentityService.upsertLookupSet(any())).thenReturn(lookupSet);
        when(coreIdentityService.upsertLookupValue(any(), any())).thenReturn(lookupValue);
        when(provisioningService.provisionUserAccess(any())).thenReturn(new ProvisionedUserAccessSummary(user, List.of(assignment), true, "secret-never-audited"));

        controller.upsertInstitutionProfile(authentication, new UpsertInstitutionProfileRequest("UZ", "University of Zimbabwe", "University of Zimbabwe", "USD", "ZW", "Africa/Harare", "{}", "{}", "UZ"));
        controller.registerUser(authentication, new RegisterUserRequest(null, "staff", "staff@uz.ac.zw", "Staff"));
        controller.provisionUserAccess(authentication, new ProvisionUserAccessRequest(null, "staff", "staff@uz.ac.zw", "Staff", null, List.of(new ProvisionedRoleAssignmentRequest(recordId, null, Instant.now()))));
        controller.updateUser(authentication, recordId, new UpdateUserRequest("Staff", null, UserStatus.ACTIVE));
        controller.deleteUser(authentication, recordId);
        controller.createRole(authentication, new CreateRoleRequest("REGISTRY", "Registry", RoleScope.SYSTEM, false));
        controller.updateRole(authentication, recordId, new UpdateRoleRequest("Registry", RoleScope.SYSTEM, false));
        controller.deleteRole(authentication, recordId);
        controller.createPermission(authentication, new CreatePermissionRequest("CORE_READ", "Read", PermissionCategory.CORE, "Read Core"));
        controller.updatePermission(authentication, recordId, new UpdatePermissionRequest("Read", PermissionCategory.CORE, "Read Core"));
        controller.deletePermission(authentication, recordId);
        controller.grantPermission(authentication, recordId, new GrantPermissionRequest(recordId));
        controller.revokePermission(authentication, recordId, recordId);
        controller.assignRole(authentication, recordId, new AssignRoleRequest(recordId, null, Instant.now()));
        controller.expireRoleAssignment(authentication, recordId, recordId);
        controller.upsertCountry(authentication, new UpsertCountryRequest("ZW", "ZWE", "Zimbabwe", "Zimbabwean"));
        controller.deleteCountry(authentication, recordId);
        controller.upsertLookupSet(authentication, new UpsertLookupSetRequest("GENDER", "Gender", "Values"));
        controller.deleteLookupSet(authentication, recordId);
        controller.upsertLookupValue(authentication, recordId, new UpsertLookupValueRequest("F", "Female", 1, true));
        controller.deleteLookupValue(authentication, recordId);

        verify(auditService, org.mockito.Mockito.times(21)).record(any(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    private JwtAuthenticationToken jwtAuthentication(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue(UUID.randomUUID().toString())
                .header("alg", "none")
                .subject(UUID.randomUUID().toString());
        claims.forEach(builder::claim);
        return new JwtAuthenticationToken(builder.build());
    }
}
