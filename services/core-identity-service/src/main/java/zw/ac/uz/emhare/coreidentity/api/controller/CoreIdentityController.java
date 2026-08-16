package zw.ac.uz.emhare.coreidentity.api.controller;

import zw.ac.uz.emhare.coreidentity.rbac.application.command.*;

import zw.ac.uz.emhare.coreidentity.api.model.*;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.AssignRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.CoreIdentityService;
import zw.ac.uz.emhare.coreidentity.rbac.CoreUserSummary;
import zw.ac.uz.emhare.coreidentity.rbac.CountrySummary;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.CreatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.CreateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.CurrentUserProfile;
import zw.ac.uz.emhare.coreidentity.rbac.InstitutionProfileSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LoginEventSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LookupSetSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LookupValueSummary;
import zw.ac.uz.emhare.coreidentity.rbac.PermissionSummary;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.ProvisionedRoleAssignmentCommand;
import zw.ac.uz.emhare.coreidentity.rbac.ProvisionedUserAccessSummary;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.ProvisionUserAccessCommand;
import zw.ac.uz.emhare.coreidentity.rbac.RbacDecision;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.RegisterUserCommand;
import zw.ac.uz.emhare.coreidentity.rbac.RolePermissionSummary;
import zw.ac.uz.emhare.coreidentity.rbac.RoleSummary;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpdatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpdateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpdateUserCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UserAccessProvisioningService;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertCountryCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertInstitutionProfileCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertLookupSetCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertLookupValueCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UserRoleAssignmentSummary;
import zw.ac.uz.emhare.coreidentity.audit.AuditEventSummary;
import zw.ac.uz.emhare.coreidentity.audit.CoreAuditService;
import zw.ac.uz.emhare.coreidentity.audit.CoreOperationalReport;

@RestController
@RequestMapping("/api/core")
public class CoreIdentityController {

    private final CoreIdentityService coreIdentityService;
    private final UserAccessProvisioningService userAccessProvisioningService;
    private final EmhareCurrentUserResolver currentUserResolver;
    private final CoreAuditService coreAuditService;

    public CoreIdentityController(
            CoreIdentityService coreIdentityService,
            UserAccessProvisioningService userAccessProvisioningService,
            EmhareCurrentUserResolver currentUserResolver,
            CoreAuditService coreAuditService) {
        this.coreIdentityService = coreIdentityService;
        this.userAccessProvisioningService = userAccessProvisioningService;
        this.currentUserResolver = currentUserResolver;
        this.coreAuditService = coreAuditService;
    }

    @GetMapping("/me")
    public CurrentUserProfile me(Authentication authentication) {
        EmhareCurrentUser currentUser = currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
        return coreIdentityService.syncAuthenticatedUser(currentUser, null, null, identitySessionId(authentication));
    }

    @GetMapping("/institution-profile")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_INSTITUTION_MANAGE')")
    public InstitutionProfileSummary institutionProfile() {
        return coreIdentityService.institutionProfile();
    }

    @GetMapping("/statistics")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_INSTITUTION_MANAGE')")
    public Map<String, Long> coreStatistics() {
        return coreIdentityService.coreStatistics();
    }

    @GetMapping("/audit-events")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_AUDIT_READ')")
    public java.util.List<AuditEventSummary> auditEvents() {
        return coreAuditService.recentEvents();
    }

    @GetMapping("/reports/overview")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_AUDIT_READ')")
    public CoreOperationalReport operationalReport() {
        return coreAuditService.operationalReport(coreIdentityService.coreStatistics());
    }

    @PutMapping("/institution-profile")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_INSTITUTION_MANAGE')")
    public InstitutionProfileSummary upsertInstitutionProfile(
            Authentication authentication,
            @Valid @RequestBody UpsertInstitutionProfileRequest request) {
        InstitutionProfileSummary before = coreIdentityService.institutionProfile();
        InstitutionProfileSummary result = coreIdentityService.upsertInstitutionProfile(new UpsertInstitutionProfileCommand(
                request.code(),
                request.name(),
                request.legalName(),
                request.registrarName(),
                request.defaultCurrencyCode(),
                request.countryCode(),
                request.timezone(),
                request.contactDetailsJson(),
                request.brandingJson(),
                request.bankDetailsJson(),
                request.legacyCode()));
        recordAudit(authentication, "CORE_INSTITUTION_PROFILE_SAVED", "INSTITUTION_PROFILE", result.id(),
                "Saved institution profile.", before, result);
        return result;
    }

    @GetMapping("/users")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public java.util.List<CoreUserSummary> listUsers() {
        return coreIdentityService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public CoreUserSummary registerUser(Authentication authentication, @Valid @RequestBody RegisterUserRequest request) {
        CoreUserSummary result = coreIdentityService.registerUser(new RegisterUserCommand(
                request.keycloakUserId(),
                request.username(),
                request.email(),
                request.displayName()));
        recordAudit(authentication, "CORE_USER_REGISTERED", "PLATFORM_USER", result.id(),
                "Registered Core user " + result.username() + ".", null, result);
        return result;
    }

    @PostMapping("/users/provisioned-access")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE') and @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public ProvisionedUserAccessSummary provisionUserAccess(
            Authentication authentication,
            @Valid @RequestBody ProvisionUserAccessRequest request) {
        ProvisionedUserAccessSummary result = userAccessProvisioningService.provisionUserAccess(new ProvisionUserAccessCommand(
                request.keycloakUserId(),
                request.username(),
                request.email(),
                request.displayName(),
                request.phoneNumber(),
                request.roleAssignments().stream()
                        .map(assignment -> new ProvisionedRoleAssignmentCommand(
                                assignment.roleId(),
                                assignment.academicUnitId(),
                                assignment.startsAt()))
                        .toList()));
        recordAudit(authentication, "CORE_USER_ACCESS_PROVISIONED", "PLATFORM_USER", result.user().id(),
                "Provisioned user access for " + result.user().username() + ".", null,
                Map.of(
                        "user", result.user(),
                        "roleAssignments", result.roleAssignments(),
                        "keycloakIdentityCreated", result.keycloakIdentityCreated()));
        return result;
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public CoreUserSummary updateUser(
            Authentication authentication,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        CoreUserSummary before = coreIdentityService.listUsers().stream()
                .filter(user -> user.id().equals(userId)).findFirst().orElse(null);
        CoreUserSummary result = coreIdentityService.updateUser(
                userId, new UpdateUserCommand(request.displayName(), request.phoneNumber(), request.status()));
        recordAudit(authentication, "CORE_USER_UPDATED", "PLATFORM_USER", userId,
                "Updated Core user " + result.username() + ".", before, result);
        return result;
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public void deleteUser(Authentication authentication, @PathVariable("userId") UUID userId) {
        CoreUserSummary before = coreIdentityService.listUsers().stream()
                .filter(user -> user.id().equals(userId)).findFirst().orElse(null);
        coreIdentityService.softDeleteUser(userId, actorUserId(authentication));
        recordAudit(authentication, "CORE_USER_DELETED", "PLATFORM_USER", userId,
                "Deleted Core user.", before, null);
    }

    @GetMapping("/roles")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE') or @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public java.util.List<RoleSummary> listRoles() {
        return coreIdentityService.listRoles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RoleSummary createRole(Authentication authentication, @Valid @RequestBody CreateRoleRequest request) {
        RoleSummary result = coreIdentityService.createRole(new CreateRoleCommand(
                request.code(),
                request.name(),
                request.scope(),
                request.systemManaged()));
        recordAudit(authentication, "CORE_ROLE_CREATED", "ROLE", result.id(),
                "Created role " + result.code() + ".", null, result);
        return result;
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RoleSummary updateRole(Authentication authentication, @PathVariable("roleId") UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
        RoleSummary before = coreIdentityService.listRoles().stream()
                .filter(role -> role.id().equals(roleId)).findFirst().orElse(null);
        RoleSummary result = coreIdentityService.updateRole(
                roleId, new UpdateRoleCommand(request.name(), request.scope(), request.systemManaged()));
        recordAudit(authentication, "CORE_ROLE_UPDATED", "ROLE", roleId,
                "Updated role " + result.code() + ".", before, result);
        return result;
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public void deleteRole(Authentication authentication, @PathVariable("roleId") UUID roleId) {
        RoleSummary before = coreIdentityService.listRoles().stream()
                .filter(role -> role.id().equals(roleId)).findFirst().orElse(null);
        coreIdentityService.softDeleteRole(roleId, actorUserId(authentication));
        recordAudit(authentication, "CORE_ROLE_DELETED", "ROLE", roleId, "Deleted role.", before, null);
    }

    @GetMapping("/permissions")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public java.util.List<PermissionSummary> listPermissions() {
        return coreIdentityService.listPermissions();
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public PermissionSummary createPermission(Authentication authentication, @Valid @RequestBody CreatePermissionRequest request) {
        PermissionSummary result = coreIdentityService.createPermission(new CreatePermissionCommand(
                request.code(),
                request.name(),
                request.category(),
                request.description()));
        recordAudit(authentication, "CORE_PERMISSION_CREATED", "PERMISSION", result.id(),
                "Created permission " + result.code() + ".", null, result);
        return result;
    }

    @PutMapping("/permissions/{permissionId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public PermissionSummary updatePermission(Authentication authentication, @PathVariable("permissionId") UUID permissionId, @Valid @RequestBody UpdatePermissionRequest request) {
        PermissionSummary before = coreIdentityService.listPermissions().stream()
                .filter(permission -> permission.id().equals(permissionId)).findFirst().orElse(null);
        PermissionSummary result = coreIdentityService.updatePermission(
                permissionId,
                new UpdatePermissionCommand(request.name(), request.category(), request.description()));
        recordAudit(authentication, "CORE_PERMISSION_UPDATED", "PERMISSION", permissionId,
                "Updated permission " + result.code() + ".", before, result);
        return result;
    }

    @DeleteMapping("/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public void deletePermission(Authentication authentication, @PathVariable("permissionId") UUID permissionId) {
        PermissionSummary before = coreIdentityService.listPermissions().stream()
                .filter(permission -> permission.id().equals(permissionId)).findFirst().orElse(null);
        coreIdentityService.softDeletePermission(permissionId, actorUserId(authentication));
        recordAudit(authentication, "CORE_PERMISSION_DELETED", "PERMISSION", permissionId,
                "Deleted permission.", before, null);
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE') or @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public java.util.List<RolePermissionSummary> listRolePermissions(@PathVariable("roleId") UUID roleId) {
        return coreIdentityService.listRolePermissions(roleId);
    }

    @PostMapping("/roles/{roleId}/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RolePermissionSummary grantPermission(
            Authentication authentication,
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody GrantPermissionRequest request) {
        RolePermissionSummary result = coreIdentityService.grantPermissionToRole(roleId, request.permissionId());
        recordAudit(authentication, "CORE_ROLE_PERMISSION_GRANTED", "ROLE", roleId,
                "Granted permission " + result.permissionCode() + " to role.", null, result);
        return result;
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public void revokePermission(
            Authentication authentication,
            @PathVariable("roleId") UUID roleId,
            @PathVariable("permissionId") UUID permissionId) {
        coreIdentityService.revokePermissionFromRole(roleId, permissionId, actorUserId(authentication));
        recordAudit(authentication, "CORE_ROLE_PERMISSION_REVOKED", "ROLE", roleId,
                "Revoked permission from role.", Map.of("permissionId", permissionId), null);
    }

    @GetMapping("/users/{userId}/role-assignments")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public java.util.List<zw.ac.uz.emhare.coreidentity.rbac.UserRoleAssignmentSummary> listUserRoleAssignments(@PathVariable("userId") UUID userId) {
        return coreIdentityService.listUserRoleAssignments(userId);
    }

    @PostMapping("/users/{userId}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public zw.ac.uz.emhare.coreidentity.rbac.UserRoleAssignmentSummary assignRole(
            Authentication authentication,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        UserRoleAssignmentSummary result = coreIdentityService.assignRole(new AssignRoleCommand(
                userId,
                request.roleId(),
                request.academicUnitId(),
                request.startsAt()));
        recordAudit(authentication, "CORE_USER_ROLE_ASSIGNED", "PLATFORM_USER", userId,
                "Assigned role " + result.roleCode() + " to user.", null, result);
        return result;
    }

    @DeleteMapping("/users/{userId}/role-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public void expireRoleAssignment(
            Authentication authentication,
            @PathVariable("userId") UUID userId,
            @PathVariable("assignmentId") UUID assignmentId) {
        coreIdentityService.expireRoleAssignment(userId, assignmentId, actorUserId(authentication));
        recordAudit(authentication, "CORE_USER_ROLE_EXPIRED", "PLATFORM_USER", userId,
                "Expired user role assignment.", Map.of("assignmentId", assignmentId), null);
    }

    @PutMapping("/users/{userId}/role-assignments/{assignmentId}/academic-unit")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public UserRoleAssignmentSummary updateRoleAssignmentAcademicUnit(
            Authentication authentication,
            @PathVariable("userId") UUID userId,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody UpdateRoleAssignmentAcademicUnitRequest request) {
        UserRoleAssignmentSummary result = coreIdentityService.updateRoleAssignmentAcademicUnit(
                userId,
                assignmentId,
                request.academicUnitId());
        recordAudit(authentication, "CORE_USER_ROLE_SCOPE_UPDATED", "PLATFORM_USER", userId,
                "Updated academic-unit scope for role " + result.roleCode() + ".",
                Map.of("assignmentId", assignmentId), result);
        return result;
    }

    @GetMapping("/users/{userId}/permissions/{permissionCode}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public RbacDecision can(
            @PathVariable("userId") UUID userId,
            @PathVariable("permissionCode") String permissionCode,
            @RequestParam(name = "academicUnitId", required = false) UUID academicUnitId) {
        return coreIdentityService.can(userId, permissionCode, academicUnitId);
    }

    @GetMapping("/countries")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public java.util.List<CountrySummary> listCountries() {
        return coreIdentityService.listCountries();
    }

    @GetMapping("/reference/countries")
    public java.util.List<CountrySummary> listActiveCountryReferenceData() {
        return coreIdentityService.listCountries();
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public CountrySummary upsertCountry(Authentication authentication, @Valid @RequestBody UpsertCountryRequest request) {
        CountrySummary result = coreIdentityService.upsertCountry(new UpsertCountryCommand(
                request.iso2Code(),
                request.iso3Code(),
                request.name(),
                request.nationalityName()));
        recordAudit(authentication, "CORE_COUNTRY_SAVED", "COUNTRY", result.id(),
                "Saved country " + result.iso2Code() + ".", null, result);
        return result;
    }

    @DeleteMapping("/countries/{countryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteCountry(Authentication authentication, @PathVariable("countryId") UUID countryId) {
        coreIdentityService.softDeleteCountry(countryId, actorUserId(authentication));
        recordAudit(authentication, "CORE_COUNTRY_DELETED", "COUNTRY", countryId,
                "Deleted country.", null, null);
    }

    @GetMapping("/lookup-sets")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public java.util.List<LookupSetSummary> listLookupSets() {
        return coreIdentityService.listLookupSets();
    }

    @PostMapping("/lookup-sets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public LookupSetSummary upsertLookupSet(Authentication authentication, @Valid @RequestBody UpsertLookupSetRequest request) {
        LookupSetSummary result = coreIdentityService.upsertLookupSet(
                new UpsertLookupSetCommand(request.code(), request.name(), request.description()));
        recordAudit(authentication, "CORE_LOOKUP_SET_SAVED", "LOOKUP_SET", result.id(),
                "Saved lookup set " + result.code() + ".", null, result);
        return result;
    }

    @DeleteMapping("/lookup-sets/{lookupSetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteLookupSet(Authentication authentication, @PathVariable("lookupSetId") UUID lookupSetId) {
        coreIdentityService.softDeleteLookupSet(lookupSetId, actorUserId(authentication));
        recordAudit(authentication, "CORE_LOOKUP_SET_DELETED", "LOOKUP_SET", lookupSetId,
                "Deleted lookup set.", null, null);
    }

    @GetMapping("/lookup-sets/{lookupSetId}/values")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public java.util.List<LookupValueSummary> listLookupValues(@PathVariable("lookupSetId") UUID lookupSetId) {
        return coreIdentityService.listLookupValues(lookupSetId);
    }

    @PostMapping("/lookup-sets/{lookupSetId}/values")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public LookupValueSummary upsertLookupValue(Authentication authentication, @PathVariable("lookupSetId") UUID lookupSetId, @Valid @RequestBody UpsertLookupValueRequest request) {
        LookupValueSummary result = coreIdentityService.upsertLookupValue(
                lookupSetId,
                new UpsertLookupValueCommand(request.code(), request.name(), request.sortOrder(), request.active()));
        recordAudit(authentication, "CORE_LOOKUP_VALUE_SAVED", "LOOKUP_VALUE", result.id(),
                "Saved lookup value " + result.code() + ".", null, result);
        return result;
    }

    @DeleteMapping("/lookup-values/{lookupValueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteLookupValue(Authentication authentication, @PathVariable("lookupValueId") UUID lookupValueId) {
        coreIdentityService.softDeleteLookupValue(lookupValueId, actorUserId(authentication));
        recordAudit(authentication, "CORE_LOOKUP_VALUE_DELETED", "LOOKUP_VALUE", lookupValueId,
                "Deleted lookup value.", null, null);
    }

    @GetMapping("/login-events")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_AUDIT_READ')")
    public java.util.List<LoginEventSummary> listRecentLoginEvents() {
        return coreIdentityService.listRecentLoginEvents();
    }

    private UUID actorUserId(Authentication authentication) {
        return currentUserResolver.fromAuthentication(authentication)
                .map(EmhareCurrentUser::auditUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
    }

    private void recordAudit(
            Authentication authentication,
            String eventType,
            String subjectType,
            UUID subjectId,
            String summary,
            Object before,
            Object after) {
        coreAuditService.record(
                actorUserId(authentication), eventType, subjectType, subjectId, summary, before, after);
    }

    private String identitySessionId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        String sessionId = jwtAuthentication.getToken().getClaimAsString("sid");
        return sessionId == null || sessionId.isBlank()
                ? jwtAuthentication.getToken().getClaimAsString("jti")
                : sessionId;
    }
}
