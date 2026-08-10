package zw.ac.uz.emhare.admissions.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

@Audited
@Entity
@Table(name = "application_types", uniqueConstraints = @UniqueConstraint(name = "uk_application_types_code", columnNames = "code"))
public class ApplicationType extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "requires_employment_history", nullable = false)
    private boolean requiresEmploymentHistory;

    @Column(name = "requires_referees", nullable = false)
    private boolean requiresReferees;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "finance_fee_structure_id")
    private UUID financeFeeStructureId;

    @Column(name = "finance_fee_structure_code", length = 50)
    private String financeFeeStructureCode;

    @Column(name = "finance_fee_structure_name", length = 160)
    private String financeFeeStructureName;

    protected ApplicationType() {
    }

    public ApplicationType(String code, String name, boolean requiresEmploymentHistory, boolean requiresReferees) {
        this(code, name, requiresEmploymentHistory, requiresReferees, true);
    }

    public ApplicationType(
            String code,
            String name,
            boolean requiresEmploymentHistory,
            boolean requiresReferees,
            boolean active) {
        this.code = requiredText(code, "Application type code").toUpperCase(Locale.ROOT);
        this.name = requiredText(name, "Application type name");
        this.requiresEmploymentHistory = requiresEmploymentHistory;
        this.requiresReferees = requiresReferees;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean requiresEmploymentHistory() {
        return requiresEmploymentHistory;
    }

    public boolean requiresReferees() {
        return requiresReferees;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getFinanceFeeStructureId() {
        return financeFeeStructureId;
    }

    public String getFinanceFeeStructureCode() {
        return financeFeeStructureCode;
    }

    public String getFinanceFeeStructureName() {
        return financeFeeStructureName;
    }

    public void update(
            String name,
            boolean requiresEmploymentHistory,
            boolean requiresReferees,
            boolean active,
            UUID financeFeeStructureId,
            String financeFeeStructureCode,
            String financeFeeStructureName,
            long expectedVersion) {
        if (getVersion() != expectedVersion) {
            throw new IllegalStateException(
                    "Application type was changed by another user. Refresh before retrying.");
        }
        this.name = requiredText(name, "Application type name");
        this.requiresEmploymentHistory = requiresEmploymentHistory;
        this.requiresReferees = requiresReferees;
        this.active = active;
        associateFeeStructure(financeFeeStructureId, financeFeeStructureCode, financeFeeStructureName);
    }

    public void associateFeeStructure(
            UUID financeFeeStructureId,
            String financeFeeStructureCode,
            String financeFeeStructureName) {
        if (financeFeeStructureId == null) {
            this.financeFeeStructureId = null;
            this.financeFeeStructureCode = null;
            this.financeFeeStructureName = null;
            return;
        }
        this.financeFeeStructureId = financeFeeStructureId;
        this.financeFeeStructureCode = requiredText(financeFeeStructureCode, "Fee structure code").toUpperCase(Locale.ROOT);
        this.financeFeeStructureName = requiredText(financeFeeStructureName, "Fee structure name");
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
