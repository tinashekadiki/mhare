package zw.ac.uz.emhare.dining.setup.domain.model;

import zw.ac.uz.emhare.dining.setup.*;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "dining_plan_meals") @SQLRestriction("deleted_at IS NULL")
public class DiningPlanMeal extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "dining_plan_id") private DiningPlan diningPlan;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meal_option_id") private MealOption mealOption;
    @Column(name = "servings_per_service", nullable = false) private int servingsPerService;
    @Column(nullable = false) private boolean monday; @Column(nullable = false) private boolean tuesday;
    @Column(nullable = false) private boolean wednesday; @Column(nullable = false) private boolean thursday;
    @Column(nullable = false) private boolean friday; @Column(nullable = false) private boolean saturday;
    @Column(nullable = false) private boolean sunday;
    protected DiningPlanMeal() {}
    public DiningPlanMeal(DiningPlan plan, MealOption option, int servings, boolean[] days) {
        if (plan == null || plan.getStatus() != DiningPlan.Status.DRAFT || option == null || !option.isActive())
            throw new IllegalArgumentException("A draft dining plan and active meal option are required.");
        if (servings < 1 || days == null || days.length != 7) throw new IllegalArgumentException("Positive servings and all seven service-day flags are required.");
        diningPlan = plan; mealOption = option; servingsPerService = servings;
        monday=days[0];tuesday=days[1];wednesday=days[2];thursday=days[3];friday=days[4];saturday=days[5];sunday=days[6];
    }
    public DiningPlan getDiningPlan() { return diningPlan; } public MealOption getMealOption() { return mealOption; }
    public int getServingsPerService() { return servingsPerService; }
    public boolean isMonday(){return monday;} public boolean isTuesday(){return tuesday;} public boolean isWednesday(){return wednesday;}
    public boolean isThursday(){return thursday;} public boolean isFriday(){return friday;} public boolean isSaturday(){return saturday;} public boolean isSunday(){return sunday;}
}
