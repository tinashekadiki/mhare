package zw.ac.uz.emhare.coreidentity.provisioning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.messaging.StudentPortalAccessProvisioningRequestedEvent;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.UserRoleAssignment;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_portal_access_provisioning")
public class StudentPortalAccessProvisioning extends AuditableEntity {
    @Column(name = "conversion_request_id", nullable = false) private UUID conversionRequestId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "student_number", nullable = false, length = 40) private String studentNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private PlatformUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_assignment_id", nullable = false) private UserRoleAssignment roleAssignment;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "provisioned_at", nullable = false) private Instant provisionedAt;

    protected StudentPortalAccessProvisioning() {
    }

    public StudentPortalAccessProvisioning(
            StudentPortalAccessProvisioningRequestedEvent event,
            PlatformUser user,
            UserRoleAssignment roleAssignment,
            Instant provisionedAt) {
        this.conversionRequestId = event.conversionRequestId();
        this.studentId = event.studentId();
        this.studentNumber = event.studentNumber();
        this.user = user;
        this.roleAssignment = roleAssignment;
        this.status = "PROVISIONED";
        this.provisionedAt = provisionedAt;
    }

    public UUID getConversionRequestId() {
        return conversionRequestId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public PlatformUser getUser() {
        return user;
    }
}
