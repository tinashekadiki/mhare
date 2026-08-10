package zw.ac.uz.emhare.finance.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** One eligible study period within a programme covered by a student discount. @author Tinashe K */
@Audited
@Entity
@Table(name = "finance_student_discount_rule_programme_periods")
@SQLRestriction("deleted_at IS NULL")
public class FinanceStudentDiscountRuleProgrammePeriod extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discount_rule_programme_id", nullable = false)
    private FinanceStudentDiscountRuleProgramme discountRuleProgramme;

    @Column(name = "programme_period_number", nullable = false)
    private int programmePeriodNumber;

    protected FinanceStudentDiscountRuleProgrammePeriod() { }

    public FinanceStudentDiscountRuleProgrammePeriod(FinanceStudentDiscountRuleProgramme discountRuleProgramme,
            int programmePeriodNumber) {
        this.discountRuleProgramme = Objects.requireNonNull(discountRuleProgramme,
                "Discount programme applicability is required.");
        if (programmePeriodNumber < 1) {
            throw new IllegalArgumentException("Programme period must be greater than zero.");
        }
        this.programmePeriodNumber = programmePeriodNumber;
    }

    public FinanceStudentDiscountRuleProgramme getDiscountRuleProgramme() { return discountRuleProgramme; }
    public int getProgrammePeriodNumber() { return programmePeriodNumber; }
}
