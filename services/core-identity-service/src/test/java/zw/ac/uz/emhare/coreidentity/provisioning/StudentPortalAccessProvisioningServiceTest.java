package zw.ac.uz.emhare.coreidentity.provisioning;

import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.provisioning.infrastructure.persistence.StudentPortalAccessProvisioningRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.UserRoleAssignmentRepository;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisioningRequestedEvent;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;

/** @author Tinashe K */
class StudentPortalAccessProvisioningServiceTest {

    private final StudentPortalAccessProvisioningRepository provisioningRepository =
            mock(StudentPortalAccessProvisioningRepository.class);
    private final PlatformUserRepository userRepository = mock(PlatformUserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRoleAssignmentRepository assignmentRepository =
            mock(UserRoleAssignmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-01-08T10:15:30Z"), ZoneOffset.UTC);
    private StudentPortalAccessProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new StudentPortalAccessProvisioningService(
                provisioningRepository, userRepository, roleRepository, assignmentRepository, clock);
    }

    @Test
    void returnsExistingProvisioningForAnExactReplay() {
        StudentPortalAccessProvisioningRequestedEvent event = event();
        PlatformUser user = mock(PlatformUser.class);
        when(user.getId()).thenReturn(event.userId());
        StudentPortalAccessProvisioning existing = new StudentPortalAccessProvisioning(
                event, user, mock(UserRoleAssignment.class), clock.instant());
        when(provisioningRepository.findByConversionRequestIdAndDeletedAtIsNull(
                event.conversionRequestId())).thenReturn(Optional.of(existing));

        StudentPortalAccessProvisioning result = service.ensureAccess(event);

        assertSame(existing, result);
        verifyNoInteractions(userRepository, roleRepository, assignmentRepository);
    }

    @Test
    void rejectsReplayWhoseStudentDoesNotMatchTheConversionRequest() {
        StudentPortalAccessProvisioningRequestedEvent original = event();
        PlatformUser user = mock(PlatformUser.class);
        when(user.getId()).thenReturn(original.userId());
        StudentPortalAccessProvisioning existing = new StudentPortalAccessProvisioning(
                original, user, mock(UserRoleAssignment.class), clock.instant());
        StudentPortalAccessProvisioningRequestedEvent conflicting =
                new StudentPortalAccessProvisioningRequestedEvent(
                        UUID.randomUUID(),
                        StudentPortalAccessProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                        clock.instant(),
                        original.conversionRequestId(),
                        UUID.randomUUID(),
                        original.studentNumber(),
                        original.userId());
        when(provisioningRepository.findByConversionRequestIdAndDeletedAtIsNull(
                original.conversionRequestId())).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.ensureAccess(conflicting));
        verifyNoInteractions(userRepository, roleRepository, assignmentRepository);
    }

    private StudentPortalAccessProvisioningRequestedEvent event() {
        return new StudentPortalAccessProvisioningRequestedEvent(
                UUID.randomUUID(),
                StudentPortalAccessProvisioningRequestedEvent.CURRENT_SCHEMA_VERSION,
                clock.instant(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "STU-2027-0000001",
                UUID.randomUUID());
    }
}
