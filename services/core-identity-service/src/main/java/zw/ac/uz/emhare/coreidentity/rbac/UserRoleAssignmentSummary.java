package zw.ac.uz.emhare.coreidentity.rbac;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAssignmentSummary(
        UUID id,
        UUID roleId,
        String roleCode,
        String roleName,
        UUID academicUnitId,
        Instant startsAt,
        Instant endsAt) {
    static UserRoleAssignmentSummary from(UserRoleAssignment assignment) {
        return new UserRoleAssignmentSummary(
                assignment.getId(),
                assignment.getRole().getId(),
                assignment.getRole().getCode(),
                assignment.getRole().getName(),
                assignment.getAcademicUnitId(),
                assignment.getStartsAt(),
                assignment.getEndsAt());
    }
}
