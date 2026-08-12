package zw.ac.uz.emhare.assessmentresults.progression.domain.model;

import zw.ac.uz.emhare.assessmentresults.progression.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "progression_rule_sets")
@SQLRestriction("deleted_at IS NULL")
public class ProgressionRuleSet extends AuditableEntity {

    public enum Status { DRAFT, APPROVED, SUPERSEDED }

    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;
    @Column(name = "rule_name", nullable = false, length = 180)
    private String ruleName;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "programme_period_number", nullable = false)
    private int programmePeriodNumber;
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;

    protected ProgressionRuleSet() {
    }

    public ProgressionRuleSet(
            String ruleCode,
            String ruleName,
            UUID programmeId,
            UUID programmeVersionId,
            int programmePeriodNumber,
            int ruleVersion) {
        this.ruleCode = required(ruleCode);
        this.ruleName = required(ruleName);
        this.programmeId = programmeId;
        this.programmeVersionId = programmeVersionId;
        if (programmePeriodNumber < 1 || ruleVersion < 1) {
            throw new IllegalArgumentException("Programme period and rule version must be positive.");
        }
        this.programmePeriodNumber = programmePeriodNumber;
        this.ruleVersion = ruleVersion;
        this.status = Status.DRAFT;
    }

    public void approve(UUID actorUserId, String reason, Instant approvedAt, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only a draft progression rule can be approved.");
        }
        this.status = Status.APPROVED;
        this.approvedByUserId = actorUserId;
        this.approvedAt = approvedAt;
        this.approvalReason = required(reason);
    }

    public void supersede() {
        if (status != Status.APPROVED) {
            throw new IllegalStateException("Only an approved progression rule can be superseded.");
        }
        status = Status.SUPERSEDED;
    }

    private void requireVersion(long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException("Progression rule changed. Refresh before retrying.");
        }
    }

    static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A reason or label is required.");
        }
        return value.trim();
    }

    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getProgrammeVersionId() { return programmeVersionId; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
    public int getRuleVersion() { return ruleVersion; }
    public Status getStatus() { return status; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReason() { return approvalReason; }
}
