package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Permission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RolePermission;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {
    boolean existsByUserAndRoleAndAcademicUnitIdAndEndsAtIsNull(PlatformUser user, Role role, UUID academicUnitId);

    List<UserRoleAssignment> findByUserIdAndEndsAtIsNull(UUID userId);

    List<UserRoleAssignment> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("""
            select distinct assignment
            from UserRoleAssignment assignment
            where assignment.role.id = :roleId
              and assignment.deletedAt is null
              and assignment.startsAt <= :asOf
              and (assignment.endsAt is null or assignment.endsAt > :asOf)
              and (:academicUnitId is null
                   or assignment.academicUnitId is null
                   or assignment.academicUnitId = :academicUnitId)
            """)
    List<UserRoleAssignment> findActiveRecipientsForRole(
            @Param("roleId") UUID roleId,
            @Param("academicUnitId") UUID academicUnitId,
            @Param("asOf") Instant asOf);

    Optional<UserRoleAssignment> findByUserAndRoleAndAcademicUnitIdIsNullAndEndsAtIsNullAndDeletedAtIsNull(
            PlatformUser user, Role role);

    @Query("""
            select distinct p.code
            from UserRoleAssignment assignment
            join RolePermission rolePermission on rolePermission.role = assignment.role
            join Permission p on p = rolePermission.permission
            where assignment.user.id = :userId
              and assignment.deletedAt is null
              and rolePermission.deletedAt is null
              and p.deletedAt is null
              and assignment.startsAt <= :asOf
              and (assignment.endsAt is null or assignment.endsAt > :asOf)
              and (assignment.academicUnitId is null or assignment.academicUnitId = :academicUnitId)
            """)
    List<String> findPermissionCodes(
            @Param("userId") UUID userId,
            @Param("academicUnitId") UUID academicUnitId,
            @Param("asOf") Instant asOf);

    @Query("""
            select distinct p.code
            from UserRoleAssignment assignment
            join RolePermission rolePermission on rolePermission.role = assignment.role
            join Permission p on p = rolePermission.permission
            where assignment.user.id = :userId
              and assignment.deletedAt is null
              and rolePermission.deletedAt is null
              and p.deletedAt is null
              and assignment.startsAt <= :asOf
              and (assignment.endsAt is null or assignment.endsAt > :asOf)
            """)
    List<String> findPermissionCodesAcrossScopes(
            @Param("userId") UUID userId,
            @Param("asOf") Instant asOf);
}
