package zw.ac.uz.emhare.coreidentity.web;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import zw.ac.uz.emhare.coreidentity.rbac.AssignRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.CoreIdentityService;
import zw.ac.uz.emhare.coreidentity.rbac.CoreUserSummary;
import zw.ac.uz.emhare.coreidentity.rbac.CountrySummary;
import zw.ac.uz.emhare.coreidentity.rbac.CreatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.CreateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.CurrentUserProfile;
import zw.ac.uz.emhare.coreidentity.rbac.InstitutionProfileSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LoginEventSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LookupSetSummary;
import zw.ac.uz.emhare.coreidentity.rbac.LookupValueSummary;
import zw.ac.uz.emhare.coreidentity.rbac.PermissionSummary;
import zw.ac.uz.emhare.coreidentity.rbac.ProvisionedRoleAssignmentCommand;
import zw.ac.uz.emhare.coreidentity.rbac.ProvisionedUserAccessSummary;
import zw.ac.uz.emhare.coreidentity.rbac.ProvisionUserAccessCommand;
import zw.ac.uz.emhare.coreidentity.rbac.RbacDecision;
import zw.ac.uz.emhare.coreidentity.rbac.RegisterUserCommand;
import zw.ac.uz.emhare.coreidentity.rbac.RolePermissionSummary;
import zw.ac.uz.emhare.coreidentity.rbac.RoleSummary;
import zw.ac.uz.emhare.coreidentity.rbac.UpdatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UpdateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UpdateUserCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UserAccessProvisioningService;
import zw.ac.uz.emhare.coreidentity.rbac.UpsertCountryCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UpsertInstitutionProfileCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UpsertLookupSetCommand;
import zw.ac.uz.emhare.coreidentity.rbac.UpsertLookupValueCommand;

@RestController
@RequestMapping("/api/core")
public class CoreIdentityController {

    private final CoreIdentityService coreIdentityService;
    private final UserAccessProvisioningService userAccessProvisioningService;
    private final EmhareCurrentUserResolver currentUserResolver;

    public CoreIdentityController(
            CoreIdentityService coreIdentityService,
            UserAccessProvisioningService userAccessProvisioningService,
            EmhareCurrentUserResolver currentUserResolver) {
        this.coreIdentityService = coreIdentityService;
        this.userAccessProvisioningService = userAccessProvisioningService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/me")
    public CurrentUserProfile me(Authentication authentication) {
        EmhareCurrentUser currentUser = currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
        return coreIdentityService.syncAuthenticatedUser(currentUser, null, null);
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

    @PutMapping("/institution-profile")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_INSTITUTION_MANAGE')")
    public InstitutionProfileSummary upsertInstitutionProfile(@Valid @RequestBody UpsertInstitutionProfileRequest request) {
        return coreIdentityService.upsertInstitutionProfile(new UpsertInstitutionProfileCommand(
                request.code(),
                request.name(),
                request.legalName(),
                request.defaultCurrencyCode(),
                request.countryCode(),
                request.timezone(),
                request.contactDetailsJson(),
                request.brandingJson(),
                request.legacyCode()));
    }

    @GetMapping("/users")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public java.util.List<CoreUserSummary> listUsers() {
        return coreIdentityService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public CoreUserSummary registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return coreIdentityService.registerUser(new RegisterUserCommand(
                request.keycloakUserId(),
                request.username(),
                request.email(),
                request.displayName()));
    }

    @PostMapping("/users/provisioned-access")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE') and @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public ProvisionedUserAccessSummary provisionUserAccess(
            @Valid @RequestBody ProvisionUserAccessRequest request) {
        return userAccessProvisioningService.provisionUserAccess(new ProvisionUserAccessCommand(
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
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public CoreUserSummary updateUser(@PathVariable("userId") UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return coreIdentityService.updateUser(userId, new UpdateUserCommand(request.displayName(), request.phoneNumber(), request.status()));
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_USER_MANAGE')")
    public void deleteUser(Authentication authentication, @PathVariable("userId") UUID userId) {
        coreIdentityService.softDeleteUser(userId, actorUserId(authentication));
    }

    @GetMapping("/roles")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE') or @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public java.util.List<RoleSummary> listRoles() {
        return coreIdentityService.listRoles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RoleSummary createRole(@Valid @RequestBody CreateRoleRequest request) {
        return coreIdentityService.createRole(new CreateRoleCommand(
                request.code(),
                request.name(),
                request.scope(),
                request.systemManaged()));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RoleSummary updateRole(@PathVariable("roleId") UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
        return coreIdentityService.updateRole(roleId, new UpdateRoleCommand(request.name(), request.scope(), request.systemManaged()));
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public void deleteRole(Authentication authentication, @PathVariable("roleId") UUID roleId) {
        coreIdentityService.softDeleteRole(roleId, actorUserId(authentication));
    }

    @GetMapping("/permissions")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public java.util.List<PermissionSummary> listPermissions() {
        return coreIdentityService.listPermissions();
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public PermissionSummary createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return coreIdentityService.createPermission(new CreatePermissionCommand(
                request.code(),
                request.name(),
                request.category(),
                request.description()));
    }

    @PutMapping("/permissions/{permissionId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public PermissionSummary updatePermission(@PathVariable("permissionId") UUID permissionId, @Valid @RequestBody UpdatePermissionRequest request) {
        return coreIdentityService.updatePermission(
                permissionId,
                new UpdatePermissionCommand(request.name(), request.category(), request.description()));
    }

    @DeleteMapping("/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_PERMISSION_MANAGE')")
    public void deletePermission(Authentication authentication, @PathVariable("permissionId") UUID permissionId) {
        coreIdentityService.softDeletePermission(permissionId, actorUserId(authentication));
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE') or @coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public java.util.List<RolePermissionSummary> listRolePermissions(@PathVariable("roleId") UUID roleId) {
        return coreIdentityService.listRolePermissions(roleId);
    }

    @PostMapping("/roles/{roleId}/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public RolePermissionSummary grantPermission(@PathVariable("roleId") UUID roleId, @Valid @RequestBody GrantPermissionRequest request) {
        return coreIdentityService.grantPermissionToRole(roleId, request.permissionId());
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_MANAGE')")
    public void revokePermission(
            Authentication authentication,
            @PathVariable("roleId") UUID roleId,
            @PathVariable("permissionId") UUID permissionId) {
        coreIdentityService.revokePermissionFromRole(roleId, permissionId, actorUserId(authentication));
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
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        return coreIdentityService.assignRole(new AssignRoleCommand(
                userId,
                request.roleId(),
                request.academicUnitId(),
                request.startsAt()));
    }

    @DeleteMapping("/users/{userId}/role-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_ROLE_ASSIGN')")
    public void expireRoleAssignment(
            Authentication authentication,
            @PathVariable("userId") UUID userId,
            @PathVariable("assignmentId") UUID assignmentId) {
        coreIdentityService.expireRoleAssignment(userId, assignmentId, actorUserId(authentication));
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
    public CountrySummary upsertCountry(@Valid @RequestBody UpsertCountryRequest request) {
        return coreIdentityService.upsertCountry(new UpsertCountryCommand(
                request.iso2Code(),
                request.iso3Code(),
                request.name(),
                request.nationalityName()));
    }

    @DeleteMapping("/countries/{countryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteCountry(Authentication authentication, @PathVariable("countryId") UUID countryId) {
        coreIdentityService.softDeleteCountry(countryId, actorUserId(authentication));
    }

    @GetMapping("/lookup-sets")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public java.util.List<LookupSetSummary> listLookupSets() {
        return coreIdentityService.listLookupSets();
    }

    @PostMapping("/lookup-sets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public LookupSetSummary upsertLookupSet(@Valid @RequestBody UpsertLookupSetRequest request) {
        return coreIdentityService.upsertLookupSet(new UpsertLookupSetCommand(request.code(), request.name(), request.description()));
    }

    @DeleteMapping("/lookup-sets/{lookupSetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteLookupSet(Authentication authentication, @PathVariable("lookupSetId") UUID lookupSetId) {
        coreIdentityService.softDeleteLookupSet(lookupSetId, actorUserId(authentication));
    }

    @GetMapping("/lookup-sets/{lookupSetId}/values")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public java.util.List<LookupValueSummary> listLookupValues(@PathVariable("lookupSetId") UUID lookupSetId) {
        return coreIdentityService.listLookupValues(lookupSetId);
    }

    @PostMapping("/lookup-sets/{lookupSetId}/values")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public LookupValueSummary upsertLookupValue(@PathVariable("lookupSetId") UUID lookupSetId, @Valid @RequestBody UpsertLookupValueRequest request) {
        return coreIdentityService.upsertLookupValue(
                lookupSetId,
                new UpsertLookupValueCommand(request.code(), request.name(), request.sortOrder(), request.active()));
    }

    @DeleteMapping("/lookup-values/{lookupValueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_REFERENCE_MANAGE')")
    public void deleteLookupValue(Authentication authentication, @PathVariable("lookupValueId") UUID lookupValueId) {
        coreIdentityService.softDeleteLookupValue(lookupValueId, actorUserId(authentication));
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
}
