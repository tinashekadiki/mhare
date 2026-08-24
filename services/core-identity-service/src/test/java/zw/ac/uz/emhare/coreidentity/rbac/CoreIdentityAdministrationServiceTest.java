package zw.ac.uz.emhare.coreidentity.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.AssignRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.CreatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.CreateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpdatePermissionCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpdateRoleCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertCountryCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertLookupSetCommand;
import zw.ac.uz.emhare.coreidentity.rbac.application.command.UpsertLookupValueCommand;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Country;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupSet;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupValue;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Permission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PermissionCategory;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RolePermission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RoleScope;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;
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

/**
 * @author Tinashe K
 */
@ExtendWith(MockitoExtension.class)
class CoreIdentityAdministrationServiceTest {

  @Mock private InstitutionProfileRepository institutionProfileRepository;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
  @Mock private CountryRepository countryRepository;
  @Mock private LookupSetRepository lookupSetRepository;
  @Mock private LookupValueRepository lookupValueRepository;
  @Mock private LoginEventRepository loginEventRepository;

  private CoreIdentityService coreIdentityService;

  @BeforeEach
  void setUp() {
    coreIdentityService =
        new CoreIdentityService(
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
  void shouldManageRolesPermissionsAndTheirGrants() {
    UUID actorUserId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID permissionId = UUID.randomUUID();
    Role role = new Role("FINANCE_OFFICER", "Finance Officer", RoleScope.SYSTEM, false);
    Permission permission =
        new Permission(
            "FINANCE_PAYMENT_VIEW",
            "View payments",
            PermissionCategory.FINANCE,
            "View captured payments.");
    ReflectionTestUtils.setField(role, "id", roleId);
    ReflectionTestUtils.setField(permission, "id", permissionId);

    when(roleRepository.findByCode("FINANCE_OFFICER")).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role savedRole = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedRole, "id", roleId);
              return savedRole;
            });
    when(roleRepository.findAll()).thenReturn(List.of(role));
    when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
    when(permissionRepository.findByCode("FINANCE_PAYMENT_VIEW")).thenReturn(Optional.empty());
    when(permissionRepository.save(any(Permission.class)))
        .thenAnswer(
            invocation -> {
              Permission savedPermission = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedPermission, "id", permissionId);
              return savedPermission;
            });
    when(permissionRepository.findAll()).thenReturn(List.of(permission));
    when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));

    RoleSummary createdRole =
        coreIdentityService.createRole(
            new CreateRoleCommand("finance officer", "Finance Officer", RoleScope.SYSTEM, false));
    PermissionSummary createdPermission =
        coreIdentityService.createPermission(
            new CreatePermissionCommand(
                "finance payment view",
                "View payments",
                PermissionCategory.FINANCE,
                "View captured payments."));

    assertEquals(roleId, createdRole.id());
    assertEquals(permissionId, createdPermission.id());
    assertEquals(1, coreIdentityService.listRoles().size());
    assertEquals(1, coreIdentityService.listPermissions().size());
    assertEquals(
        "Finance Operations",
        coreIdentityService
            .updateRole(roleId, new UpdateRoleCommand("Finance Operations", RoleScope.SYSTEM, true))
            .name());
    assertEquals(
        "Review payments",
        coreIdentityService
            .updatePermission(
                permissionId,
                new UpdatePermissionCommand(
                    "Review payments", PermissionCategory.FINANCE, "Review captured payments."))
            .name());

    RolePermission grant = new RolePermission(role, permission);
    ReflectionTestUtils.setField(grant, "id", UUID.randomUUID());
    when(rolePermissionRepository.findByRoleAndPermission(role, permission))
        .thenReturn(Optional.empty(), Optional.of(grant));
    when(rolePermissionRepository.save(any(RolePermission.class)))
        .thenAnswer(
            invocation -> {
              RolePermission savedGrant = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedGrant, "id", grant.getId());
              return savedGrant;
            });
    when(rolePermissionRepository.findByRoleIdAndDeletedAtIsNull(roleId))
        .thenReturn(List.of(grant));

    assertEquals(
        permissionId,
        coreIdentityService.grantPermissionToRole(roleId, permissionId).permissionId());
    assertEquals(1, coreIdentityService.listRolePermissions(roleId).size());
    coreIdentityService.revokePermissionFromRole(roleId, permissionId, actorUserId);
    assertTrue(grant.isDeleted());

    coreIdentityService.softDeleteRole(roleId, actorUserId);
    coreIdentityService.softDeletePermission(permissionId, actorUserId);
    assertTrue(role.isDeleted());
    assertTrue(permission.isDeleted());
  }

  @Test
  void shouldManageRoleAssignmentsAndUserRetirement() {
    UUID actorUserId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID firstAcademicUnitId = UUID.randomUUID();
    UUID secondAcademicUnitId = UUID.randomUUID();
    PlatformUser user =
        new PlatformUser(
            UUID.randomUUID(), "reviewer@example.test", "reviewer@example.test", "Reviewer");
    Role role = new Role("ACADEMIC_REVIEWER", "Academic Reviewer", RoleScope.ACADEMIC_UNIT, false);
    ReflectionTestUtils.setField(user, "id", userId);
    ReflectionTestUtils.setField(role, "id", roleId);
    UserRoleAssignment[] savedAssignment = new UserRoleAssignment[1];

    when(platformUserRepository.findById(userId)).thenReturn(Optional.of(user));
    when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
    when(userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
            user, role, firstAcademicUnitId))
        .thenReturn(false);
    when(userRoleAssignmentRepository.existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(
            user, role, secondAcademicUnitId))
        .thenReturn(false);
    when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class)))
        .thenAnswer(
            invocation -> {
              savedAssignment[0] = invocation.getArgument(0);
              ReflectionTestUtils.setField(savedAssignment[0], "id", UUID.randomUUID());
              return savedAssignment[0];
            });

    UserRoleAssignmentSummary created =
        coreIdentityService.assignRole(
            new AssignRoleCommand(userId, roleId, firstAcademicUnitId, Instant.now()));
    when(userRoleAssignmentRepository.findById(created.id()))
        .thenReturn(Optional.of(savedAssignment[0]));
    when(userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId))
        .thenReturn(List.of(savedAssignment[0]));

    assertEquals(firstAcademicUnitId, created.academicUnitId());
    assertEquals(1, coreIdentityService.listUserRoleAssignments(userId).size());
    assertEquals(
        firstAcademicUnitId,
        coreIdentityService
            .updateRoleAssignmentAcademicUnit(userId, created.id(), firstAcademicUnitId)
            .academicUnitId());
    assertEquals(
        secondAcademicUnitId,
        coreIdentityService
            .updateRoleAssignmentAcademicUnit(userId, created.id(), secondAcademicUnitId)
            .academicUnitId());

    coreIdentityService.expireRoleAssignment(userId, created.id(), actorUserId);
    assertTrue(savedAssignment[0].isDeleted());
    coreIdentityService.softDeleteUser(userId, actorUserId);
    assertTrue(user.isDeleted());
  }

  @Test
  void shouldManageCountriesAndLookupReferenceData() {
    UUID actorUserId = UUID.randomUUID();
    UUID countryId = UUID.randomUUID();
    UUID lookupSetId = UUID.randomUUID();
    UUID lookupValueId = UUID.randomUUID();
    Country country = new Country("ZW", "ZWE", "Zimbabwe", "Zimbabwean");
    LookupSet lookupSet = new LookupSet("DOCUMENT_TYPES", "Document types", "Accepted documents");
    LookupValue lookupValue = new LookupValue(lookupSet, "PASSPORT", "Passport", 10, true);
    ReflectionTestUtils.setField(country, "id", countryId);
    ReflectionTestUtils.setField(lookupSet, "id", lookupSetId);
    ReflectionTestUtils.setField(lookupValue, "id", lookupValueId);

    when(countryRepository.findByIso2Code("ZW")).thenReturn(Optional.of(country));
    when(countryRepository.save(country)).thenReturn(country);
    when(countryRepository.findByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(country));
    when(countryRepository.findById(countryId)).thenReturn(Optional.of(country));
    when(lookupSetRepository.findByCode("DOCUMENT_TYPES")).thenReturn(Optional.of(lookupSet));
    when(lookupSetRepository.save(lookupSet)).thenReturn(lookupSet);
    when(lookupSetRepository.findByDeletedAtIsNullOrderByCodeAsc()).thenReturn(List.of(lookupSet));
    when(lookupSetRepository.findById(lookupSetId)).thenReturn(Optional.of(lookupSet));
    when(lookupValueRepository.findByLookupSetAndCode(lookupSet, "PASSPORT"))
        .thenReturn(Optional.of(lookupValue));
    when(lookupValueRepository.save(lookupValue)).thenReturn(lookupValue);
    when(lookupValueRepository.findByLookupSetIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(
            lookupSetId))
        .thenReturn(List.of(lookupValue));
    when(lookupValueRepository.findById(lookupValueId)).thenReturn(Optional.of(lookupValue));

    assertEquals(
        countryId,
        coreIdentityService
            .upsertCountry(new UpsertCountryCommand("ZW", "ZWE", "Zimbabwe", "Zimbabwean"))
            .id());
    assertEquals(1, coreIdentityService.listCountries().size());
    assertEquals(
        lookupSetId,
        coreIdentityService
            .upsertLookupSet(
                new UpsertLookupSetCommand(
                    "document types", "Identity documents", "Accepted identity documents"))
            .id());
    assertEquals(1, coreIdentityService.listLookupSets().size());
    assertEquals(
        lookupValueId,
        coreIdentityService
            .upsertLookupValue(
                lookupSetId, new UpsertLookupValueCommand("passport", "Passport", 20, false))
            .id());
    assertEquals(1, coreIdentityService.listLookupValues(lookupSetId).size());
    assertFalse(coreIdentityService.listLookupValues(lookupSetId).getFirst().active());

    coreIdentityService.softDeleteCountry(countryId, actorUserId);
    coreIdentityService.softDeleteLookupSet(lookupSetId, actorUserId);
    coreIdentityService.softDeleteLookupValue(lookupValueId, actorUserId);
    assertTrue(country.isDeleted());
    assertTrue(lookupSet.isDeleted());
    assertTrue(lookupValue.isDeleted());
  }
}
