package zw.ac.uz.emhare.dining.setup;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited
@Entity
@Table(name = "dining_hall_assignment_rules")
@SQLRestriction("deleted_at IS NULL")
public class DiningHallAssignmentRule extends AuditableEntity {
    public enum Dimension { SURNAME_PREFIX, RESIDENCE_HALL, PROGRAMME, STUDENT_GROUP }
    public enum Operator { EQUALS, STARTS_WITH, IN }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dining_hall_id")
    private DiningHall diningHall;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_dimension", nullable = false, length = 30)
    private Dimension ruleDimension;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 20)
    private Operator comparisonOperator;

    @Column(name = "comparison_value", nullable = false, length = 200)
    private String comparisonValue;

    @Column(name = "priority_rank", nullable = false)
    private int priorityRank;

    @Column(nullable = false)
    private boolean active;

    protected DiningHallAssignmentRule() {}

    public DiningHallAssignmentRule(DiningHall diningHall, Dimension ruleDimension, Operator comparisonOperator,
            String comparisonValue, int priorityRank) {
        updateValues(diningHall, ruleDimension, comparisonOperator, comparisonValue, priorityRank, true);
    }

    public void update(DiningHall diningHall, Dimension ruleDimension, Operator comparisonOperator,
            String comparisonValue, int priorityRank, boolean active, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Dining hall assignment rule");
        updateValues(diningHall, ruleDimension, comparisonOperator, comparisonValue, priorityRank, active);
    }

    private void updateValues(DiningHall diningHall, Dimension ruleDimension, Operator comparisonOperator,
            String comparisonValue, int priorityRank, boolean active) {
        if (diningHall == null || !diningHall.isActive()) {
            throw new IllegalArgumentException("An active dining hall is required.");
        }
        if (ruleDimension == null || comparisonOperator == null) {
            throw new IllegalArgumentException("A rule dimension and comparison operator are required.");
        }
        if (priorityRank < 1) {
            throw new IllegalArgumentException("Rule priority must be positive.");
        }
        this.diningHall = diningHall;
        this.ruleDimension = ruleDimension;
        this.comparisonOperator = comparisonOperator;
        this.comparisonValue = DiningValues.required(comparisonValue, "Rule comparison value");
        this.priorityRank = priorityRank;
        this.active = active;
    }

    public DiningHall getDiningHall() { return diningHall; }
    public Dimension getRuleDimension() { return ruleDimension; }
    public Operator getComparisonOperator() { return comparisonOperator; }
    public String getComparisonValue() { return comparisonValue; }
    public int getPriorityRank() { return priorityRank; }
    public boolean isActive() { return active; }
}
