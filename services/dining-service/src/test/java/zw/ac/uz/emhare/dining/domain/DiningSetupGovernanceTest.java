package zw.ac.uz.emhare.dining.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.dining.setup.domain.model.*;

/**
 * @author Tinashe K
 */
class DiningSetupGovernanceTest extends DiningGovernanceFixture {
  private static final LocalTime OPENS = LocalTime.of(11, 0);
  private static final LocalTime CLOSES = LocalTime.of(14, 0);

  @ParameterizedTest
  @ValueSource(strings = {"version", "from", "preparer", "reversed"})
  void planVersionsRequireValidEffectiveScopeAndPreparationEvidence(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DiningPlan(
                "FULL",
                invalid.equals("version") ? 0 : 1,
                "Full Board",
                null,
                null,
                invalid.equals("from") ? null : START,
                invalid.equals("reversed") ? START.minusDays(1) : END,
                invalid.equals("preparer") ? null : MAKER));
  }

  @Test
  void planHasIndependentApprovalAndForwardOnlyActivationRetirementLifecycle() {
    var plan = draftPlan();
    assertThrows(
        IllegalArgumentException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, null, "Approved", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, MAKER, "Self approval", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> plan.transition(DiningPlan.Status.RETIRED, CHECKER, "Skip activation", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, CHECKER, "Stale approval", NOW, 1));
    plan.transition(DiningPlan.Status.ACTIVE, CHECKER, " Approved verified meals ", NOW, 0);
    assertEquals(CHECKER, plan.getApprovedByUserId());
    assertEquals(NOW, plan.getApprovedAt());
    assertEquals("Approved verified meals", plan.getApprovalReason());
    assertThrows(
        IllegalStateException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, CHECKER, "Repeated activation", NOW, 0));
    plan.transition(DiningPlan.Status.RETIRED, CHECKER, "Replaced by next version", NOW, 0);
    assertEquals(DiningPlan.Status.RETIRED, plan.getStatus());
    assertThrows(
        IllegalStateException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, CHECKER, "Reactivate history", NOW, 0));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plan",
        "active-plan",
        "option",
        "inactive-option",
        "servings",
        "days",
        "day-count"
      })
  void mealEntitlementsCanOnlyBeConfiguredOnDraftPlansWithCompleteWeekFlags(String invalid) {
    if (invalid.equals("inactive-option")) deactivateOption();
    var plan = invalid.equals("active-plan") ? activePlan() : draftPlan();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DiningPlanMeal(
                invalid.equals("plan") ? null : plan,
                invalid.equals("option") ? null : option,
                invalid.equals("servings") ? 0 : 1,
                invalid.equals("days") ? null : new boolean[invalid.equals("day-count") ? 6 : 7]));
  }

  @Test
  void specificDayMealEntitlementRetainsServingsAndPlanOwnership() {
    var plan = draftPlan();
    var meal =
        new DiningPlanMeal(
            plan, option, 2, new boolean[] {true, false, true, false, true, false, true});
    assertSame(plan, meal.getDiningPlan());
    assertSame(option, meal.getMealOption());
    assertEquals(2, meal.getServingsPerService());
    assertTrue(meal.isMonday());
    assertFalse(meal.isTuesday());
    assertTrue(meal.isWednesday());
    assertFalse(meal.isThursday());
    assertTrue(meal.isFriday());
    assertFalse(meal.isSaturday());
    assertTrue(meal.isSunday());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hall",
        "inactive-hall",
        "option",
        "inactive-option",
        "day-low",
        "day-high",
        "opens",
        "closes",
        "grace",
        "same-time",
        "early-grace"
      })
  void mealServiceTimesRejectInactiveReferencesAndInvalidClockBounds(String invalid) {
    if (invalid.equals("inactive-hall")) deactivateHall();
    if (invalid.equals("inactive-option")) deactivateOption();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MealServiceTime(
                invalid.equals("hall") ? null : hall,
                invalid.equals("option") ? null : option,
                invalid.equals("day-low") ? 0 : invalid.equals("day-high") ? 8 : 1,
                invalid.equals("opens") ? null : OPENS,
                invalid.equals("closes") ? null : invalid.equals("same-time") ? OPENS : CLOSES,
                invalid.equals("grace")
                    ? null
                    : invalid.equals("early-grace") ? CLOSES.minusMinutes(1) : CLOSES));
  }

  @Test
  void serviceTimeCorrectionsAreVersionedAndPermitGraceEqualToClosing() {
    var time = new MealServiceTime(hall, option, 1, OPENS, CLOSES, CLOSES);
    assertThrows(
        IllegalStateException.class,
        () -> time.update(hall, option, 2, OPENS, CLOSES, CLOSES, false, 1));
    assertEquals(1, time.getDayOfWeek());
    assertTrue(time.isActive());
    time.update(hall, option, 7, OPENS.plusHours(1), CLOSES, CLOSES.plusMinutes(15), false, 0);
    assertEquals(7, time.getDayOfWeek());
    assertEquals(OPENS.plusHours(1), time.getServiceOpensAt());
    assertEquals(CLOSES.plusMinutes(15), time.getGraceClosesAt());
    assertFalse(time.isActive());
  }

  @ParameterizedTest
  @ValueSource(strings = {"hall", "inactive-hall", "staff", "from", "role", "reversed"})
  void attendantsRequireActiveHallOwnedStaffAndValidEffectiveWindow(String invalid) {
    if (invalid.equals("inactive-hall")) deactivateHall();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DiningAttendantAssignment(
                invalid.equals("hall") ? null : hall,
                invalid.equals("staff") ? null : ATTENDANT,
                "STAFF-1",
                "Attendant",
                invalid.equals("from") ? null : START,
                invalid.equals("reversed") ? START.minusDays(1) : END,
                invalid.equals("role") ? null : DiningAttendantAssignment.Role.ATTENDANT));
  }

  @Test
  void staffAssignmentCanBeCorrectedAndDeactivatedOnlyAtItsCurrentVersion() {
    var assignment =
        new DiningAttendantAssignment(
            hall,
            ATTENDANT,
            " staff-1 ",
            " Attendant ",
            START,
            null,
            DiningAttendantAssignment.Role.ATTENDANT);
    assertThrows(
        IllegalStateException.class,
        () ->
            assignment.update(
                hall,
                ATTENDANT,
                "STAFF-1",
                "Attendant",
                START,
                END,
                DiningAttendantAssignment.Role.SUPERVISOR,
                false,
                1));
    assertTrue(assignment.isActive());
    assertEquals(DiningAttendantAssignment.Role.ATTENDANT, assignment.getRoleCode());
    assignment.update(
        hall,
        ATTENDANT,
        "STAFF-1",
        "Supervising Attendant",
        START,
        END,
        DiningAttendantAssignment.Role.SUPERVISOR,
        false,
        0);
    assertFalse(assignment.isActive());
    assertEquals(DiningAttendantAssignment.Role.SUPERVISOR, assignment.getRoleCode());
    assertEquals(END, assignment.getEffectiveUntil());
    assertEquals("Supervising Attendant", assignment.getStaffName());
  }

  @ParameterizedTest
  @ValueSource(strings = {"hall", "inactive-hall", "dimension", "operator", "priority"})
  void hallAssignmentRulesRejectUnusableRoutingCriteria(String invalid) {
    if (invalid.equals("inactive-hall")) deactivateHall();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DiningHallAssignmentRule(
                invalid.equals("hall") ? null : hall,
                invalid.equals("dimension") ? null : DiningHallAssignmentRule.Dimension.PROGRAMME,
                invalid.equals("operator") ? null : DiningHallAssignmentRule.Operator.EQUALS,
                "CSC",
                invalid.equals("priority") ? 0 : 1));
  }

  @Test
  void routingRuleCorrectionRetainsCurrentVersionAndNormalizesComparisonEvidence() {
    var rule =
        new DiningHallAssignmentRule(
            hall,
            DiningHallAssignmentRule.Dimension.PROGRAMME,
            DiningHallAssignmentRule.Operator.EQUALS,
            "CSC",
            1);
    assertThrows(
        IllegalStateException.class,
        () ->
            rule.update(
                hall,
                DiningHallAssignmentRule.Dimension.STUDENT_GROUP,
                DiningHallAssignmentRule.Operator.IN,
                "YEAR1,YEAR2",
                2,
                false,
                1));
    assertEquals(DiningHallAssignmentRule.Dimension.PROGRAMME, rule.getRuleDimension());
    rule.update(
        hall,
        DiningHallAssignmentRule.Dimension.STUDENT_GROUP,
        DiningHallAssignmentRule.Operator.IN,
        " YEAR1,YEAR2 ",
        2,
        false,
        0);
    assertEquals("YEAR1,YEAR2", rule.getComparisonValue());
    assertEquals(2, rule.getPriorityRank());
    assertFalse(rule.isActive());
  }

  @Test
  void operationalHallAndMealCorrectionsHaveValidationAndOptimisticLocking() {
    assertThrows(IllegalArgumentException.class, () -> new DiningHall("MAIN", "Main", "Campus", 0));
    assertThrows(
        IllegalArgumentException.class, () -> new MealOption("LUNCH", "Lunch", null, null));
    assertThrows(
        IllegalStateException.class,
        () -> hall.update("WEST", "West Hall", "West Campus", 50, false, 1));
    assertThrows(
        IllegalStateException.class,
        () -> option.update("DINNER", "Dinner", null, MealOption.Category.DINNER, false, 1));
    hall.update(" west ", " West Hall ", " West Campus ", 50, false, 0);
    option.update(" dinner ", " Dinner ", " Evening meal ", MealOption.Category.DINNER, false, 0);
    assertEquals("WEST", hall.getCode());
    assertEquals("West Campus", hall.getLocationDescription());
    assertEquals(50, hall.getServiceCapacity());
    assertEquals("DINNER", option.getCode());
    assertEquals("Evening meal", option.getDescription());
    assertFalse(option.isActive());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void requiredSetupLabelsCannotBeBlankWhileOptionalDescriptionsCan(String text) {
    assertThrows(IllegalArgumentException.class, () -> new DiningHall(text, "Hall", "Campus", 1));
    var option = new MealOption("LUNCH", "Lunch", text, MealOption.Category.LUNCH);
    assertNull(option.getDescription());
    var plan = draftPlan();
    assertThrows(
        IllegalArgumentException.class,
        () -> plan.transition(DiningPlan.Status.ACTIVE, CHECKER, text, NOW, 0));
    assertEquals(DiningPlan.Status.DRAFT, plan.getStatus());
  }
}
