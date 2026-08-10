package zw.ac.uz.emhare.dining.setup;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
interface DiningHallRepository extends JpaRepository<DiningHall, UUID> { List<DiningHall> findAllByDeletedAtIsNullOrderByCodeAsc(); }
interface MealOptionRepository extends JpaRepository<MealOption, UUID> { List<MealOption> findAllByDeletedAtIsNullOrderByCodeAsc(); }
interface MealServiceTimeRepository extends JpaRepository<MealServiceTime, UUID> { List<MealServiceTime> findAllByDeletedAtIsNullOrderByDiningHallCodeAscDayOfWeekAscServiceOpensAtAsc(); }
interface DiningPlanRepository extends JpaRepository<DiningPlan, UUID> { List<DiningPlan> findAllByDeletedAtIsNullOrderByCodeAscPlanVersionDesc(); }
interface DiningPlanMealRepository extends JpaRepository<DiningPlanMeal, UUID> {
    List<DiningPlanMeal> findAllByDeletedAtIsNullOrderByDiningPlanCodeAscMealOptionCodeAsc();
    long countByDiningPlanId(UUID diningPlanId);
}
interface DiningHallAssignmentRuleRepository extends JpaRepository<DiningHallAssignmentRule, UUID> {
    List<DiningHallAssignmentRule> findAllByDeletedAtIsNullOrderByPriorityRankAscDiningHallCodeAsc();
}
interface DiningAttendantAssignmentRepository extends JpaRepository<DiningAttendantAssignment, UUID> {
    List<DiningAttendantAssignment> findAllByDeletedAtIsNullOrderByDiningHallCodeAscStaffNumberAsc();
}
