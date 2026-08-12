package zw.ac.uz.emhare.coreidentity.provisioning;

import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.provisioning.infrastructure.persistence.StudentPortalAccessProvisioningRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.UserRoleAssignmentRepository;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisioningRequestedEvent;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;

/** @author Tinashe K */
@Service
public class StudentPortalAccessProvisioningService {
    private final StudentPortalAccessProvisioningRepository provisioningRepository;
    private final PlatformUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final Clock clock;

    public StudentPortalAccessProvisioningService(
            StudentPortalAccessProvisioningRepository provisioningRepository,
            PlatformUserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository assignmentRepository,
            Clock clock) {
        this.provisioningRepository = provisioningRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    @Transactional
    public StudentPortalAccessProvisioning ensureAccess(StudentPortalAccessProvisioningRequestedEvent event) {
        StudentPortalAccessProvisioning existing = provisioningRepository
                .findByConversionRequestIdAndDeletedAtIsNull(event.conversionRequestId()).orElse(null);
        if (existing != null) {
            if (!existing.getStudentId().equals(event.studentId())
                    || !existing.getUser().getId().equals(event.userId())
                    || !existing.getStudentNumber().equals(event.studentNumber())) {
                throw new IllegalStateException(
                        "Existing portal access provisioning conflicts with the conversion request.");
            }
            return existing;
        }
        PlatformUser user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalArgumentException("Student portal user not found."));
        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new IllegalStateException("System-managed STUDENT role is unavailable."));
        UserRoleAssignment assignment = assignmentRepository
                .findByUserAndRoleAndAcademicUnitIdIsNullAndEndsAtIsNullAndDeletedAtIsNull(user, studentRole)
                .orElseGet(() -> assignmentRepository.saveAndFlush(
                        new UserRoleAssignment(user, studentRole, null, clock.instant())));
        return provisioningRepository.saveAndFlush(
                new StudentPortalAccessProvisioning(event, user, assignment, clock.instant()));
    }
}
