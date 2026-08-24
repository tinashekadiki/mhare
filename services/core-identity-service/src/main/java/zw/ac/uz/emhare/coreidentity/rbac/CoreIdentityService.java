package zw.ac.uz.emhare.coreidentity.rbac;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Country;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.InstitutionProfile;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LoginEvent;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LoginOutcome;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupSet;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupValue;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Permission;
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

@Service
public class CoreIdentityService {

  private static final String DEFAULT_INSTITUTION_BRANDING_JSON =
      "{\"primaryColor\":\"#001f6e\",\"secondaryColor\":\"#cb920e\"}";

  private static final Set<String> SELF_SERVICE_ROLE_CODES = Set.of("APPLICANT", "STUDENT");

  private final InstitutionProfileRepository institutionProfileRepository;
  private final PlatformUserRepository platformUserRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRoleAssignmentRepository userRoleAssignmentRepository;
  private final CountryRepository countryRepository;
  private final LookupSetRepository lookupSetRepository;
  private final LookupValueRepository lookupValueRepository;
  private final LoginEventRepository loginEventRepository;

  public CoreIdentityService(
      InstitutionProfileRepository institutionProfileRepository,
      PlatformUserRepository platformUserRepository,
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      RolePermissionRepository rolePermissionRepository,
      UserRoleAssignmentRepository userRoleAssignmentRepository,
      CountryRepository countryRepository,
      LookupSetRepository lookupSetRepository,
      LookupValueRepository lookupValueRepository,
      LoginEventRepository loginEventRepository) {
    this.institutionProfileRepository = institutionProfileRepository;
    this.platformUserRepository = platformUserRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    this.countryRepository = countryRepository;
    this.lookupSetRepository = lookupSetRepository;
    this.lookupValueRepository = lookupValueRepository;
    this.loginEventRepository = loginEventRepository;
  }

  @Transactional
  public CurrentUserProfile syncAuthenticatedUser(EmhareCurrentUser currentUser) {
    return syncAuthenticatedUser(currentUser, null, null);
  }

  @Transactional
  public CurrentUserProfile syncAuthenticatedUser(
      EmhareCurrentUser currentUser, String ipAddress, String userAgent) {
    return syncAuthenticatedUser(currentUser, ipAddress, userAgent, null);
  }

  @Transactional
  public CurrentUserProfile syncAuthenticatedUser(
      EmhareCurrentUser currentUser, String ipAddress, String userAgent, String identitySessionId) {
    if (currentUser.keycloakUserId() == null) {
      throw new IllegalArgumentException("Authenticated Keycloak user id is required.");
    }
    platformUserRepository.acquireIdentitySynchronizationLock(currentUser.keycloakUserId());
    PlatformUser user =
        platformUserRepository
            .findByKeycloakUserId(currentUser.keycloakUserId())
            .or(
                () ->
                    currentUser.email() == null
                        ? java.util.Optional.empty()
                        : platformUserRepository.findByEmail(currentUser.email()))
            .orElseGet(
                () ->
                    platformUserRepository.save(
                        new PlatformUser(
                            currentUser.keycloakUserId(),
                            currentUser.username(),
                            currentUser.email(),
                            currentUser.displayName())));
    user.syncFromIdentityProvider(
        currentUser.keycloakUserId(),
        currentUser.username(),
        currentUser.email(),
        currentUser.displayName());
    platformUserRepository.save(user);
    if (identitySessionId == null
        || identitySessionId.isBlank()
        || !loginEventRepository.existsByKeycloakUserIdAndIdentitySessionIdAndDeletedAtIsNull(
            currentUser.keycloakUserId(), identitySessionId)) {
      loginEventRepository.save(
          new LoginEvent(
              user,
              currentUser.keycloakUserId(),
              currentUser.username(),
              currentUser.email(),
              ipAddress,
              userAgent,
              identitySessionId,
              LoginOutcome.SUCCESS));
    }
    assignDefaultRoleIfNeeded(user, currentUser);
    activateIfAccessProfileComplete(user, currentUser.realmRoles());
    return currentUserProfile(user.getId(), currentUser.realmRoles());
  }

  @Transactional(readOnly = true)
  public InstitutionProfileSummary institutionProfile() {
    return institutionProfileRepository
        .findFirstByDeletedAtIsNullOrderByCreatedAtAsc()
        .map(InstitutionProfileSummary::from)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public Map<String, Long> coreStatistics() {
    InstitutionProfileRepository.CoreStatisticsProjection statistics =
        institutionProfileRepository.loadCoreStatistics();
    return Map.of(
        "userCount", statistics.getUserCount(),
        "roleCount", statistics.getRoleCount(),
        "permissionCount", statistics.getPermissionCount(),
        "lookupSetCount", statistics.getLookupSetCount());
  }

  @Transactional
  public InstitutionProfileSummary upsertInstitutionProfile(
      UpsertInstitutionProfileCommand command) {
    InstitutionProfile profile =
        institutionProfileRepository
            .findFirstByDeletedAtIsNullOrderByCreatedAtAsc()
            .orElseGet(
                () ->
                    new InstitutionProfile(
                        command.code(),
                        command.name(),
                        command.legalName(),
                        command.countryCode(),
                        command.timezone()));
    profile.update(
        normalizeCode(command.code()),
        command.name(),
        command.legalName(),
        command.registrarName(),
        command.defaultCurrencyCode(),
        command.countryCode(),
        command.timezone(),
        command.contactDetailsJson(),
        command.brandingJson(),
        command.bankDetailsJson(),
        command.legacyCode());
    return InstitutionProfileSummary.from(institutionProfileRepository.save(profile));
  }

  @Transactional
  public CoreUserSummary registerUser(RegisterUserCommand command) {
    PlatformUser user =
        command.keycloakUserId() == null
            ? platformUserRepository.findByEmail(command.email()).orElse(null)
            : platformUserRepository.findByKeycloakUserId(command.keycloakUserId()).orElse(null);
    if (user == null) {
      user =
          platformUserRepository.save(
              new PlatformUser(
                  command.keycloakUserId(),
                  command.username(),
                  command.email(),
                  command.displayName()));
    }
    return CoreUserSummary.from(user);
  }

  @Transactional
  public ProvisionedUserAccessSummary provisionUserAccess(ProvisionUserAccessCommand command) {
    if (command.roleAssignments() == null || command.roleAssignments().isEmpty()) {
      throw new IllegalArgumentException(
          "At least one role assignment is required to complete a user access profile.");
    }
    if (command.keycloakUserId() == null) {
      throw new IllegalArgumentException("Keycloak user id is required to provision user access.");
    }
    PlatformUser user =
        platformUserRepository
            .findByKeycloakUserId(command.keycloakUserId())
            .or(() -> platformUserRepository.findByEmail(command.email()))
            .orElse(null);
    if (user == null) {
      user =
          platformUserRepository.save(
              new PlatformUser(
                  command.keycloakUserId(),
                  command.username(),
                  command.email(),
                  command.displayName()));
    } else {
      user.linkIdentityProvider(
          command.keycloakUserId(), command.username(), command.email(), command.displayName());
    }
    user.updateProfile(command.displayName(), command.phoneNumber(), UserStatus.INVITED);

    Set<String> assignmentKeys = new HashSet<>();
    Instant activationTime = Instant.now();
    for (ProvisionedRoleAssignmentCommand assignmentCommand : command.roleAssignments()) {
      Role role =
          roleRepository
              .findById(assignmentCommand.roleId())
              .orElseThrow(() -> new IllegalArgumentException("Role not found."));
      validateScope(role, assignmentCommand.academicUnitId());
      requireRoleGrantsAccess(role);
      String assignmentKey = role.getId() + ":" + assignmentCommand.academicUnitId();
      if (!assignmentKeys.add(assignmentKey)) {
        throw new IllegalArgumentException(
            "The same role and scope cannot be assigned more than once.");
      }
      Instant startsAt =
          assignmentCommand.startsAt() == null ? activationTime : assignmentCommand.startsAt();
      if (startsAt.isAfter(activationTime)) {
        throw new IllegalArgumentException("Provisioned role assignments must start immediately.");
      }
      if (!userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
          user, role, assignmentCommand.academicUnitId())) {
        userRoleAssignmentRepository.save(
            new UserRoleAssignment(user, role, assignmentCommand.academicUnitId(), startsAt));
      }
    }

    user.activate();
    platformUserRepository.save(user);
    return new ProvisionedUserAccessSummary(
        CoreUserSummary.from(user),
        userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(user.getId()).stream()
            .map(UserRoleAssignmentSummary::from)
            .toList(),
        false,
        null);
  }

  @Transactional(readOnly = true)
  public List<CoreUserSummary> listUsers() {
    return platformUserRepository.findAll().stream()
        .filter(user -> !user.isDeleted())
        .map(CoreUserSummary::from)
        .toList();
  }

  @Transactional
  public CoreUserSummary updateUser(UUID userId, UpdateUserCommand command) {
    PlatformUser user =
        platformUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    if (command.status() == UserStatus.ACTIVE && !hasCompleteAccessProfile(userId, Instant.now())) {
      throw new IllegalStateException(
          "Assign a role with usable access before activating this user.");
    }
    user.updateProfile(command.displayName(), command.phoneNumber(), command.status());
    return CoreUserSummary.from(user);
  }

  @Transactional(readOnly = true)
  public CurrentUserProfile currentUserProfile(UUID userId) {
    return currentUserProfile(userId, Set.of());
  }

  private CurrentUserProfile currentUserProfile(UUID userId, Set<String> realmRoles) {
    PlatformUser user =
        platformUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    Instant now = Instant.now();
    List<UserRoleAssignment> activeRoleAssignments =
        userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
            .filter(assignment -> !assignment.getStartsAt().isAfter(now))
            .filter(
                assignment -> assignment.getEndsAt() == null || assignment.getEndsAt().isAfter(now))
            .toList();
    Set<String> normalizedRealmRoles =
        new LinkedHashSet<>(realmRoles == null ? Set.of() : realmRoles);
    Set<String> effectivePermissionCodes =
        user.getStatus() == UserStatus.ACTIVE
            ? new TreeSet<>(
                userRoleAssignmentRepository.findPermissionCodesAcrossScopes(userId, now))
            : Set.of();
    boolean hasOperationalAssignment =
        activeRoleAssignments.stream()
            .map(assignment -> assignment.getRole().getCode())
            .anyMatch(roleCode -> !SELF_SERVICE_ROLE_CODES.contains(roleCode));
    boolean operationalAccess =
        user.getStatus() == UserStatus.ACTIVE
            && (normalizedRealmRoles.contains("system-admin")
                || (hasOperationalAssignment && !effectivePermissionCodes.isEmpty()));
    String institutionBrandingJson =
        institutionProfileRepository
            .findFirstByDeletedAtIsNullOrderByCreatedAtAsc()
            .map(InstitutionProfile::getBrandingJson)
            .filter(brandingJson -> !brandingJson.isBlank())
            .orElse(DEFAULT_INSTITUTION_BRANDING_JSON);
    return new CurrentUserProfile(
        CoreUserSummary.from(user),
        activeRoleAssignments.stream().map(UserRoleAssignmentSummary::from).toList(),
        Set.copyOf(normalizedRealmRoles),
        Set.copyOf(effectivePermissionCodes),
        operationalAccess,
        institutionBrandingJson);
  }

  @Transactional
  public RoleSummary createRole(CreateRoleCommand command) {
    Role role =
        roleRepository
            .findByCode(normalizeCode(command.code()))
            .orElseGet(
                () ->
                    roleRepository.save(
                        new Role(
                            normalizeCode(command.code()),
                            command.name(),
                            command.scope(),
                            command.systemManaged())));
    return RoleSummary.from(role);
  }

  @Transactional(readOnly = true)
  public List<RoleSummary> listRoles() {
    return roleRepository.findAll().stream()
        .filter(role -> !role.isDeleted())
        .map(RoleSummary::from)
        .toList();
  }

  @Transactional
  public RoleSummary updateRole(UUID roleId, UpdateRoleCommand command) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));
    role.update(command.name(), command.scope(), command.systemManaged());
    return RoleSummary.from(role);
  }

  @Transactional
  public void softDeleteRole(UUID roleId, UUID actorUserId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));
    role.markDeleted(actorUserId);
  }

  @Transactional
  public PermissionSummary createPermission(CreatePermissionCommand command) {
    Permission permission =
        permissionRepository
            .findByCode(normalizeCode(command.code()))
            .orElseGet(
                () ->
                    permissionRepository.save(
                        new Permission(
                            normalizeCode(command.code()),
                            command.name(),
                            command.category(),
                            command.description())));
    return PermissionSummary.from(permission);
  }

  @Transactional(readOnly = true)
  public List<PermissionSummary> listPermissions() {
    return permissionRepository.findAll().stream()
        .filter(permission -> !permission.isDeleted())
        .map(PermissionSummary::from)
        .toList();
  }

  @Transactional
  public PermissionSummary updatePermission(UUID permissionId, UpdatePermissionCommand command) {
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found."));
    permission.update(command.name(), command.category(), command.description());
    return PermissionSummary.from(permission);
  }

  @Transactional
  public void softDeletePermission(UUID permissionId, UUID actorUserId) {
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found."));
    permission.markDeleted(actorUserId);
  }

  @Transactional
  public RolePermissionSummary grantPermissionToRole(UUID roleId, UUID permissionId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found."));
    RolePermission rolePermission =
        rolePermissionRepository
            .findByRoleAndPermission(role, permission)
            .orElseGet(() -> rolePermissionRepository.save(new RolePermission(role, permission)));
    return RolePermissionSummary.from(rolePermission);
  }

  @Transactional(readOnly = true)
  public List<RolePermissionSummary> listRolePermissions(UUID roleId) {
    return rolePermissionRepository.findByRoleIdAndDeletedAtIsNull(roleId).stream()
        .map(RolePermissionSummary::from)
        .toList();
  }

  @Transactional
  public void revokePermissionFromRole(UUID roleId, UUID permissionId, UUID actorUserId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found."));
    RolePermission rolePermission =
        rolePermissionRepository
            .findByRoleAndPermission(role, permission)
            .orElseThrow(() -> new IllegalArgumentException("Role permission grant not found."));
    rolePermission.markDeleted(actorUserId);
  }

  @Transactional
  public UserRoleAssignmentSummary assignRole(AssignRoleCommand command) {
    PlatformUser user =
        platformUserRepository
            .findById(command.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    Role role =
        roleRepository
            .findById(command.roleId())
            .orElseThrow(() -> new IllegalArgumentException("Role not found."));
    validateScope(role, command.academicUnitId());
    if (userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
        user, role, command.academicUnitId())) {
      throw new IllegalStateException("User already has this active role assignment.");
    }
    UserRoleAssignment assignment =
        userRoleAssignmentRepository.save(
            new UserRoleAssignment(
                user,
                role,
                command.academicUnitId(),
                command.startsAt() == null ? Instant.now() : command.startsAt()));
    return UserRoleAssignmentSummary.from(assignment);
  }

  @Transactional(readOnly = true)
  public List<UserRoleAssignmentSummary> listUserRoleAssignments(UUID userId) {
    return userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
        .map(UserRoleAssignmentSummary::from)
        .toList();
  }

  @Transactional
  public UserRoleAssignmentSummary updateRoleAssignmentAcademicUnit(
      UUID userId, UUID assignmentId, UUID academicUnitId) {
    UserRoleAssignment assignment =
        userRoleAssignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new IllegalArgumentException("Role assignment not found."));
    if (!assignment.getUser().getId().equals(userId)) {
      throw new IllegalArgumentException("Role assignment does not belong to the user.");
    }
    if (assignment.isDeleted() || assignment.getEndsAt() != null) {
      throw new IllegalStateException("Only an active role assignment can be updated.");
    }
    Role role = assignment.getRole();
    validateScope(role, academicUnitId);
    if (Objects.equals(academicUnitId, assignment.getAcademicUnitId())) {
      return UserRoleAssignmentSummary.from(assignment);
    }
    if (userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
        assignment.getUser(), role, academicUnitId)) {
      throw new IllegalStateException("User already has this active role assignment.");
    }
    assignment.assignAcademicUnit(academicUnitId);
    return UserRoleAssignmentSummary.from(assignment);
  }

  @Transactional(readOnly = true)
  public RbacDecision can(UUID userId, String permissionCode, UUID academicUnitId) {
    Set<String> permissionCodes =
        new HashSet<>(
            userRoleAssignmentRepository.findPermissionCodes(
                userId, academicUnitId, Instant.now()));
    return new RbacDecision(
        userId, academicUnitId, permissionCode, permissionCodes.contains(permissionCode));
  }

  @Transactional
  public void expireRoleAssignment(UUID userId, UUID assignmentId, UUID actorUserId) {
    UserRoleAssignment assignment =
        userRoleAssignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new IllegalArgumentException("Role assignment not found."));
    if (!assignment.getUser().getId().equals(userId)) {
      throw new IllegalArgumentException("Role assignment does not belong to the user.");
    }
    assignment.end(Instant.now());
    assignment.markDeleted(actorUserId);
  }

  @Transactional
  public void softDeleteUser(UUID userId, UUID actorUserId) {
    PlatformUser user =
        platformUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    user.updateProfile(user.getDisplayName(), user.getPhoneNumber(), UserStatus.DISABLED);
    user.markDeleted(actorUserId);
  }

  @Transactional(readOnly = true)
  public List<CountrySummary> listCountries() {
    return countryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
        .map(CountrySummary::from)
        .toList();
  }

  @Transactional
  public CountrySummary upsertCountry(UpsertCountryCommand command) {
    Country country =
        countryRepository
            .findByIso2Code(command.iso2Code())
            .orElseGet(
                () ->
                    new Country(
                        command.iso2Code(),
                        command.iso3Code(),
                        command.name(),
                        command.nationalityName()));
    country.update(command.iso3Code(), command.name(), command.nationalityName());
    return CountrySummary.from(countryRepository.save(country));
  }

  @Transactional
  public void softDeleteCountry(UUID countryId, UUID actorUserId) {
    Country country =
        countryRepository
            .findById(countryId)
            .orElseThrow(() -> new IllegalArgumentException("Country not found."));
    country.markDeleted(actorUserId);
  }

  @Transactional(readOnly = true)
  public List<LookupSetSummary> listLookupSets() {
    return lookupSetRepository.findByDeletedAtIsNullOrderByCodeAsc().stream()
        .map(LookupSetSummary::from)
        .toList();
  }

  @Transactional
  public LookupSetSummary upsertLookupSet(UpsertLookupSetCommand command) {
    LookupSet lookupSet =
        lookupSetRepository
            .findByCode(normalizeCode(command.code()))
            .orElseGet(
                () ->
                    new LookupSet(
                        normalizeCode(command.code()), command.name(), command.description()));
    lookupSet.update(command.name(), command.description());
    return LookupSetSummary.from(lookupSetRepository.save(lookupSet));
  }

  @Transactional
  public void softDeleteLookupSet(UUID lookupSetId, UUID actorUserId) {
    LookupSet lookupSet =
        lookupSetRepository
            .findById(lookupSetId)
            .orElseThrow(() -> new IllegalArgumentException("Lookup set not found."));
    lookupSet.markDeleted(actorUserId);
  }

  @Transactional(readOnly = true)
  public List<LookupValueSummary> listLookupValues(UUID lookupSetId) {
    return lookupValueRepository
        .findByLookupSetIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(lookupSetId)
        .stream()
        .map(LookupValueSummary::from)
        .toList();
  }

  @Transactional
  public LookupValueSummary upsertLookupValue(UUID lookupSetId, UpsertLookupValueCommand command) {
    LookupSet lookupSet =
        lookupSetRepository
            .findById(lookupSetId)
            .orElseThrow(() -> new IllegalArgumentException("Lookup set not found."));
    LookupValue lookupValue =
        lookupValueRepository
            .findByLookupSetAndCode(lookupSet, normalizeCode(command.code()))
            .orElseGet(
                () ->
                    new LookupValue(
                        lookupSet,
                        normalizeCode(command.code()),
                        command.name(),
                        command.sortOrder(),
                        command.active()));
    lookupValue.update(command.name(), command.sortOrder(), command.active());
    return LookupValueSummary.from(lookupValueRepository.save(lookupValue));
  }

  @Transactional
  public void softDeleteLookupValue(UUID lookupValueId, UUID actorUserId) {
    LookupValue lookupValue =
        lookupValueRepository
            .findById(lookupValueId)
            .orElseThrow(() -> new IllegalArgumentException("Lookup value not found."));
    lookupValue.markDeleted(actorUserId);
  }

  @Transactional(readOnly = true)
  public List<LoginEventSummary> listRecentLoginEvents() {
    return loginEventRepository.findTop100ByDeletedAtIsNullOrderByOccurredAtDesc().stream()
        .map(LoginEventSummary::from)
        .toList();
  }

  private void validateScope(Role role, UUID academicUnitId) {
    if (role.getScope() == RoleScope.SYSTEM && academicUnitId != null) {
      throw new IllegalArgumentException("System roles cannot be scoped to an academic unit.");
    }
    if (role.getScope() == RoleScope.ACADEMIC_UNIT && academicUnitId == null) {
      throw new IllegalArgumentException("Academic-unit roles require an academic unit.");
    }
  }

  private void assignDefaultRoleIfNeeded(PlatformUser user, EmhareCurrentUser currentUser) {
    boolean mappedRealmRole = false;
    for (String realmRole : currentUser.realmRoles()) {
      String localRoleCode = normalizeCode(realmRole.replace('-', '_'));
      Role localRole = roleRepository.findByCode(localRoleCode).orElse(null);
      if (localRole == null || localRole.getScope() != RoleScope.SYSTEM) {
        continue;
      }
      mappedRealmRole = true;
      assignRoleIfMissing(user, localRole);
    }
    if (!mappedRealmRole) {
      boolean alreadyHasLocalAccess =
          !userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(user.getId()).isEmpty();
      if (!alreadyHasLocalAccess) {
        roleRepository.findByCode("APPLICANT").ifPresent(role -> assignRoleIfMissing(user, role));
      }
    }
  }

  private void assignRoleIfMissing(PlatformUser user, Role role) {
    if (!userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
        user, role, null)) {
      userRoleAssignmentRepository.save(new UserRoleAssignment(user, role, null, Instant.now()));
    }
  }

  private void activateIfAccessProfileComplete(PlatformUser user, Set<String> realmRoles) {
    if (user.getStatus() == UserStatus.INVITED
        && ((realmRoles != null && realmRoles.contains("system-admin"))
            || hasCompleteAccessProfile(user.getId(), Instant.now()))) {
      user.activate();
      platformUserRepository.save(user);
    }
  }

  private boolean hasCompleteAccessProfile(UUID userId, Instant asOf) {
    List<UserRoleAssignment> activeAssignments =
        userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
            .filter(assignment -> assignment.isActiveAt(asOf))
            .toList();
    if (activeAssignments.stream()
        .map(assignment -> assignment.getRole().getCode())
        .anyMatch(SELF_SERVICE_ROLE_CODES::contains)) {
      return true;
    }
    return !activeAssignments.isEmpty()
        && !userRoleAssignmentRepository.findPermissionCodesAcrossScopes(userId, asOf).isEmpty();
  }

  private void requireRoleGrantsAccess(Role role) {
    if (!SELF_SERVICE_ROLE_CODES.contains(role.getCode())
        && rolePermissionRepository.findByRoleIdAndDeletedAtIsNull(role.getId()).isEmpty()) {
      throw new IllegalStateException(
          "Role "
              + role.getName()
              + " has no permissions and cannot complete a user access profile.");
    }
  }

  private String normalizeCode(String value) {
    return value == null ? null : value.trim().toUpperCase().replace(' ', '_');
  }
}
