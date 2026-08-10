package zw.ac.uz.emhare.finance.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Approved programme applicability snapshot for a discount scope. @author Tinashe K */
@Audited
@Entity
@Table(name = "finance_student_discount_rule_programmes")
@SQLRestriction("deleted_at IS NULL")
public class FinanceStudentDiscountRuleProgramme extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discount_rule_id", nullable = false)
    private FinanceStudentDiscountRule discountRule;
    @Column(name = "programme_id", nullable = false) private UUID programmeId;
    @Column(name = "programme_code", nullable = false, length = 80) private String programmeCode;
    @Column(name = "programme_name", nullable = false, length = 200) private String programmeName;

    protected FinanceStudentDiscountRuleProgramme() { }
    public FinanceStudentDiscountRuleProgramme(FinanceStudentDiscountRule discountRule, UUID programmeId,
            String programmeCode, String programmeName) {
        this.discountRule = Objects.requireNonNull(discountRule);
        this.programmeId = Objects.requireNonNull(programmeId, "Applicable programme is required.");
        this.programmeCode = FinanceFeeCatalogue.required(programmeCode, "Programme code").toUpperCase(Locale.ROOT);
        this.programmeName = FinanceFeeCatalogue.required(programmeName, "Programme name");
    }
    public FinanceStudentDiscountRule getDiscountRule() { return discountRule; }
    public UUID getProgrammeId() { return programmeId; } public String getProgrammeCode() { return programmeCode; }
    public String getProgrammeName() { return programmeName; }
}
