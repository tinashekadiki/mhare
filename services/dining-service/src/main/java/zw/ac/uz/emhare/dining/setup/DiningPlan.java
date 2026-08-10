package zw.ac.uz.emhare.dining.setup;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "dining_plans") @SQLRestriction("deleted_at IS NULL")
public class DiningPlan extends AuditableEntity {
    public enum Status { DRAFT, ACTIVE, RETIRED }
    @Column(nullable = false, length = 40) private String code;
    @Column(name = "plan_version", nullable = false) private int planVersion;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 500) private String description;
    @Column(name = "finance_fee_catalogue_id") private UUID financeFeeCatalogueId;
    @Column(name = "valid_from", nullable = false) private LocalDate validFrom;
    @Column(name = "valid_until") private LocalDate validUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "prepared_by_user_id", nullable = false) private UUID preparedByUserId;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000) private String approvalReason;
    protected DiningPlan() {}
    public DiningPlan(String code, int version, String name, String description, UUID financeFeeCatalogueId,
            LocalDate validFrom, LocalDate validUntil, UUID preparer) {
        if (version < 1 || validFrom == null || preparer == null) throw new IllegalArgumentException("Plan version, validity start, and preparing operator are required.");
        if (validUntil != null && validUntil.isBefore(validFrom)) throw new IllegalArgumentException("Plan validity end cannot precede its start.");
        this.code = DiningValues.code(code, "Dining plan code"); planVersion = version;
        this.name = DiningValues.required(name, "Dining plan name"); this.description = DiningValues.optional(description);
        this.financeFeeCatalogueId = financeFeeCatalogueId; this.validFrom = validFrom; this.validUntil = validUntil;
        preparedByUserId = preparer; status = Status.DRAFT;
    }
    public void transition(Status target, UUID actor, String reason, Instant occurredAt, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Dining plan");
        if (actor == null || actor.equals(preparedByUserId)) throw new IllegalArgumentException("A different authorised operator must approve the dining plan.");
        boolean allowed = status == Status.DRAFT && target == Status.ACTIVE || status == Status.ACTIVE && target == Status.RETIRED;
        if (!allowed) throw new IllegalStateException("Dining plan cannot move from " + status + " to " + target + ".");
        approvedByUserId = actor; approvedAt = occurredAt; approvalReason = DiningValues.required(reason, "Approval reason"); status = target;
    }
    public String getCode() { return code; } public int getPlanVersion() { return planVersion; }
    public String getName() { return name; } public String getDescription() { return description; }
    public UUID getFinanceFeeCatalogueId() { return financeFeeCatalogueId; } public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; } public Status getStatus() { return status; }
    public UUID getPreparedByUserId() { return preparedByUserId; } public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; } public String getApprovalReason() { return approvalReason; }
}
