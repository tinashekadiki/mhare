package zw.ac.uz.emhare.studentrecords.conversion.domain.model;

import zw.ac.uz.emhare.studentrecords.conversion.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "student_conversion_requests")
public class StudentConversionRequest extends AuditableEntity {

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;
    @Column(name = "source_application_id", nullable = false)
    private UUID sourceApplicationId;
    @Column(name = "source_offer_id", nullable = false)
    private UUID sourceOfferId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_enrolment_id", nullable = false)
    private StudentProgrammeEnrolment programmeEnrolment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudentConversionStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "finance_provisioning_status", nullable = false, length = 30)
    private ProvisioningStatus financeProvisioningStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "portal_provisioning_status", nullable = false, length = 30)
    private ProvisioningStatus portalProvisioningStatus;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "last_retry_at")
    private Instant lastRetryAt;
    @Column(name = "last_retry_by_user_id")
    private UUID lastRetryByUserId;
    @Column(name = "last_retry_reason", length = 1000)
    private String lastRetryReason;

    protected StudentConversionRequest() {
    }

    public StudentConversionRequest(
            UUID sourceEventId,
            UUID sourceApplicationId,
            UUID sourceOfferId,
            StudentProfile student,
            StudentProgrammeEnrolment programmeEnrolment,
            Instant requestedAt) {
        this.sourceEventId = sourceEventId;
        this.sourceApplicationId = sourceApplicationId;
        this.sourceOfferId = sourceOfferId;
        this.student = student;
        this.programmeEnrolment = programmeEnrolment;
        this.status = StudentConversionStatus.PROVISIONING;
        this.financeProvisioningStatus = ProvisioningStatus.PENDING;
        this.portalProvisioningStatus = ProvisioningStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public boolean recordFinanceProvisioning(boolean successful, String reason) {
        if (financeProvisioningStatus != ProvisioningStatus.PENDING) return false;
        financeProvisioningStatus = successful ? ProvisioningStatus.COMPLETED : ProvisioningStatus.FAILED;
        if (!successful) fail("Finance provisioning failed: " + reason);
        return true;
    }

    public boolean recordPortalProvisioning(boolean successful, String reason) {
        if (portalProvisioningStatus != ProvisioningStatus.PENDING) return false;
        portalProvisioningStatus = successful ? ProvisioningStatus.COMPLETED : ProvisioningStatus.FAILED;
        if (!successful) fail("Portal provisioning failed: " + reason);
        return true;
    }

    public boolean canComplete() {
        return status == StudentConversionStatus.PROVISIONING
                && financeProvisioningStatus == ProvisioningStatus.COMPLETED
                && portalProvisioningStatus == ProvisioningStatus.COMPLETED;
    }

    public void complete(Instant now) {
        if (!canComplete()) throw new IllegalStateException("Both provisioning operations must complete first.");
        student.activate(now);
        programmeEnrolment.activate(now);
        status = StudentConversionStatus.COMPLETED;
        completedAt = now;
    }

    public void retryProvisioning(String reason, UUID actorUserId, Instant now) {
        if (status == StudentConversionStatus.COMPLETED) {
            throw new IllegalStateException("A completed student conversion cannot be retried.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A conversion retry reason is required.");
        }
        if (actorUserId == null || now == null) {
            throw new IllegalArgumentException("Conversion retry actor and timestamp are required.");
        }
        if (financeProvisioningStatus == ProvisioningStatus.FAILED) {
            financeProvisioningStatus = ProvisioningStatus.PENDING;
        }
        if (portalProvisioningStatus == ProvisioningStatus.FAILED) {
            portalProvisioningStatus = ProvisioningStatus.PENDING;
        }
        if (financeProvisioningStatus != ProvisioningStatus.PENDING
                && portalProvisioningStatus != ProvisioningStatus.PENDING) {
            throw new IllegalStateException("This conversion has no provisioning operation to retry.");
        }
        status = StudentConversionStatus.PROVISIONING;
        failureReason = null;
        retryCount++;
        lastRetryAt = now;
        lastRetryByUserId = actorUserId;
        lastRetryReason = reason.trim();
    }

    public boolean needsFinanceProvisioning() {
        return financeProvisioningStatus == ProvisioningStatus.PENDING;
    }

    public boolean needsPortalProvisioning() {
        return portalProvisioningStatus == ProvisioningStatus.PENDING;
    }

    private void fail(String reason) {
        status = StudentConversionStatus.FAILED;
        failureReason = reason == null ? "Provisioning failed without a reason." : reason;
    }

    public UUID getSourceApplicationId() { return sourceApplicationId; }
    public UUID getSourceOfferId() { return sourceOfferId; }
    public StudentProfile getStudent() { return student; }
    public StudentProgrammeEnrolment getProgrammeEnrolment() { return programmeEnrolment; }
    public StudentConversionStatus getStatus() { return status; }
    public ProvisioningStatus getFinanceProvisioningStatus() { return financeProvisioningStatus; }
    public ProvisioningStatus getPortalProvisioningStatus() { return portalProvisioningStatus; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public Instant getLastRetryAt() { return lastRetryAt; }
    public UUID getLastRetryByUserId() { return lastRetryByUserId; }
    public String getLastRetryReason() { return lastRetryReason; }
}
