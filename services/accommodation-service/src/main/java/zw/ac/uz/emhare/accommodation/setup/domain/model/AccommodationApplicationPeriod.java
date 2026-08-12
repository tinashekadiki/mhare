package zw.ac.uz.emhare.accommodation.setup.domain.model;

import zw.ac.uz.emhare.accommodation.setup.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "accommodation_application_periods")
@SQLRestriction("deleted_at IS NULL")
public class AccommodationApplicationPeriod extends AuditableEntity {
    public enum Status { DRAFT, APPLICATION_OPEN, APPLICATION_CLOSED, ALLOCATION_ACTIVE, CLOSED }

    @Column(name = "academic_period_id", nullable = false) private UUID academicPeriodId;
    @Column(name = "academic_period_code", nullable = false, length = 50) private String academicPeriodCode;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "applications_open_at", nullable = false) private Instant applicationsOpenAt;
    @Column(name = "applications_close_at", nullable = false) private Instant applicationsCloseAt;
    @Column(name = "occupancy_starts_on", nullable = false) private LocalDate occupancyStartsOn;
    @Column(name = "occupancy_ends_on", nullable = false) private LocalDate occupancyEndsOn;
    @Column(name = "allocation_cutoff_at", nullable = false) private Instant allocationCutoffAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(name = "prepared_by_user_id", nullable = false) private UUID preparedByUserId;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000) private String approvalReason;

    protected AccommodationApplicationPeriod() {}

    public AccommodationApplicationPeriod(UUID academicPeriodId, String academicPeriodCode, String code,
            String name, Instant applicationsOpenAt, Instant applicationsCloseAt,
            LocalDate occupancyStartsOn, LocalDate occupancyEndsOn, Instant allocationCutoffAt,
            UUID preparedByUserId) {
        updateDraftValues(academicPeriodId, academicPeriodCode, code, name, applicationsOpenAt,
                applicationsCloseAt, occupancyStartsOn, occupancyEndsOn, allocationCutoffAt);
        if (preparedByUserId == null) throw new IllegalArgumentException("Preparing operator is required.");
        this.preparedByUserId = preparedByUserId;
        status = Status.DRAFT;
    }

    public void updateDraft(UUID academicPeriodId, String academicPeriodCode, String code, String name,
            Instant applicationsOpenAt, Instant applicationsCloseAt, LocalDate occupancyStartsOn,
            LocalDate occupancyEndsOn, Instant allocationCutoffAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.DRAFT) throw new IllegalStateException("Only draft accommodation periods can be edited.");
        updateDraftValues(academicPeriodId, academicPeriodCode, code, name, applicationsOpenAt,
                applicationsCloseAt, occupancyStartsOn, occupancyEndsOn, allocationCutoffAt);
    }

    public void transition(Status targetStatus, UUID actorUserId, String reason, Instant occurredAt,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (actorUserId == null || actorUserId.equals(preparedByUserId)) {
            throw new IllegalArgumentException("A different authorised operator must approve the accommodation period.");
        }
        boolean allowed = switch (status) {
            case DRAFT -> targetStatus == Status.APPLICATION_OPEN;
            case APPLICATION_OPEN -> targetStatus == Status.APPLICATION_CLOSED;
            case APPLICATION_CLOSED -> targetStatus == Status.ALLOCATION_ACTIVE;
            case ALLOCATION_ACTIVE -> targetStatus == Status.CLOSED;
            case CLOSED -> false;
        };
        if (!allowed) throw new IllegalStateException("Accommodation period cannot move from " + status + " to " + targetStatus + ".");
        approvedByUserId = actorUserId;
        approvedAt = occurredAt;
        approvalReason = AccommodationPremise.required(reason, "Approval reason");
        status = targetStatus;
    }

    private void updateDraftValues(UUID academicPeriodId, String academicPeriodCode, String code,
            String name, Instant applicationsOpenAt, Instant applicationsCloseAt,
            LocalDate occupancyStartsOn, LocalDate occupancyEndsOn, Instant allocationCutoffAt) {
        if (academicPeriodId == null || applicationsOpenAt == null || applicationsCloseAt == null
                || occupancyStartsOn == null || occupancyEndsOn == null || allocationCutoffAt == null) {
            throw new IllegalArgumentException("Academic period and all accommodation dates are required.");
        }
        if (!applicationsCloseAt.isAfter(applicationsOpenAt)) throw new IllegalArgumentException("Applications must close after they open.");
        if (occupancyEndsOn.isBefore(occupancyStartsOn)) throw new IllegalArgumentException("Occupancy end date cannot precede its start date.");
        if (allocationCutoffAt.isBefore(applicationsCloseAt)) throw new IllegalArgumentException("Allocation cutoff cannot precede application closure.");
        this.academicPeriodId = academicPeriodId;
        this.academicPeriodCode = AccommodationPremise.required(academicPeriodCode, "Academic period code").toUpperCase();
        this.code = AccommodationPremise.required(code, "Accommodation period code").toUpperCase();
        this.name = AccommodationPremise.required(name, "Accommodation period name");
        this.applicationsOpenAt = applicationsOpenAt;
        this.applicationsCloseAt = applicationsCloseAt;
        this.occupancyStartsOn = occupancyStartsOn;
        this.occupancyEndsOn = occupancyEndsOn;
        this.allocationCutoffAt = allocationCutoffAt;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) throw new IllegalStateException("The accommodation period was changed by another operator. Refresh and try again.");
    }

    public UUID getAcademicPeriodId() { return academicPeriodId; }
    public String getAcademicPeriodCode() { return academicPeriodCode; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Instant getApplicationsOpenAt() { return applicationsOpenAt; }
    public Instant getApplicationsCloseAt() { return applicationsCloseAt; }
    public LocalDate getOccupancyStartsOn() { return occupancyStartsOn; }
    public LocalDate getOccupancyEndsOn() { return occupancyEndsOn; }
    public Instant getAllocationCutoffAt() { return allocationCutoffAt; }
    public Status getStatus() { return status; }
    public UUID getPreparedByUserId() { return preparedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReason() { return approvalReason; }
}
