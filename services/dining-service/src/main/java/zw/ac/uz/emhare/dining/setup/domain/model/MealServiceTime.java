package zw.ac.uz.emhare.dining.setup.domain.model;

import zw.ac.uz.emhare.dining.setup.*;

import jakarta.persistence.*;
import java.time.LocalTime;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "meal_service_times") @SQLRestriction("deleted_at IS NULL")
public class MealServiceTime extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "dining_hall_id") private DiningHall diningHall;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meal_option_id") private MealOption mealOption;
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "day_of_week", nullable = false) private int dayOfWeek;
    @Column(name = "service_opens_at", nullable = false) private LocalTime serviceOpensAt;
    @Column(name = "service_closes_at", nullable = false) private LocalTime serviceClosesAt;
    @Column(name = "grace_closes_at", nullable = false) private LocalTime graceClosesAt;
    @Column(nullable = false) private boolean active;
    protected MealServiceTime() {}
    public MealServiceTime(DiningHall hall, MealOption option, int dayOfWeek, LocalTime opens, LocalTime closes, LocalTime graceCloses) {
        updateValues(hall, option, dayOfWeek, opens, closes, graceCloses, true);
    }
    public void update(DiningHall hall, MealOption option, int dayOfWeek, LocalTime opens, LocalTime closes,
            LocalTime graceCloses, boolean active, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Meal service time");
        updateValues(hall, option, dayOfWeek, opens, closes, graceCloses, active);
    }
    private void updateValues(DiningHall hall, MealOption option, int dayOfWeek, LocalTime opens, LocalTime closes,
            LocalTime graceCloses, boolean active) {
        if (hall == null || !hall.isActive() || option == null || !option.isActive()) throw new IllegalArgumentException("An active dining hall and meal option are required.");
        if (dayOfWeek < 1 || dayOfWeek > 7) throw new IllegalArgumentException("Day of week must be between 1 and 7.");
        if (opens == null || closes == null || graceCloses == null || !closes.isAfter(opens) || graceCloses.isBefore(closes))
            throw new IllegalArgumentException("Meal service and grace times are invalid.");
        diningHall = hall; mealOption = option; this.dayOfWeek = dayOfWeek; serviceOpensAt = opens;
        serviceClosesAt = closes; this.graceClosesAt = graceCloses; this.active = active;
    }
    public DiningHall getDiningHall() { return diningHall; } public MealOption getMealOption() { return mealOption; }
    public int getDayOfWeek() { return dayOfWeek; } public LocalTime getServiceOpensAt() { return serviceOpensAt; }
    public LocalTime getServiceClosesAt() { return serviceClosesAt; } public LocalTime getGraceClosesAt() { return graceClosesAt; }
    public boolean isActive() { return active; }
}
