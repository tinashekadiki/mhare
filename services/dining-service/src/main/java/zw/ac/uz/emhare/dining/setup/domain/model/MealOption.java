package zw.ac.uz.emhare.dining.setup.domain.model;

import zw.ac.uz.emhare.dining.setup.*;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** @author Tinashe K */
@Audited @Entity @Table(name = "meal_options") @SQLRestriction("deleted_at IS NULL")
public class MealOption extends AuditableEntity {
    public enum Category { BREAKFAST, LUNCH, DINNER, OTHER }
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "meal_category", nullable = false, length = 20) private Category mealCategory;
    @Column(nullable = false) private boolean active;
    protected MealOption() {}
    public MealOption(String code, String name, String description, Category category) { updateValues(code, name, description, category, true); }
    public void update(String code, String name, String description, Category category, boolean active, long expectedVersion) {
        DiningValues.version(getVersion(), expectedVersion, "Meal option"); updateValues(code, name, description, category, active);
    }
    private void updateValues(String code, String name, String description, Category category, boolean active) {
        if (category == null) throw new IllegalArgumentException("Meal category is required.");
        this.code = DiningValues.code(code, "Meal option code"); this.name = DiningValues.required(name, "Meal option name");
        this.description = DiningValues.optional(description); this.mealCategory = category; this.active = active;
    }
    public String getCode() { return code; } public String getName() { return name; }
    public String getDescription() { return description; } public Category getMealCategory() { return mealCategory; }
    public boolean isActive() { return active; }
}
