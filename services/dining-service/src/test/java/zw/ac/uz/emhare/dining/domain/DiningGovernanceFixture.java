package zw.ac.uz.emhare.dining.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import zw.ac.uz.emhare.dining.operations.domain.model.MealServiceSession;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDiningAssignment;
import zw.ac.uz.emhare.dining.setup.domain.model.DiningHall;
import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlan;
import zw.ac.uz.emhare.dining.setup.domain.model.MealOption;

/** Real dining aggregates and independent workflow operators. @author Tinashe K */
abstract class DiningGovernanceFixture {
  protected static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  protected static final LocalDate START = LocalDate.of(2026, 8, 1);
  protected static final LocalDate END = LocalDate.of(2026, 12, 31);
  protected static final UUID MAKER = UUID.randomUUID();
  protected static final UUID CHECKER = UUID.randomUUID();
  protected static final UUID ATTENDANT = UUID.randomUUID();
  protected static final UUID STUDENT = UUID.randomUUID();
  protected static final UUID PERIOD = UUID.randomUUID();
  protected final DiningHall hall = new DiningHall("MAIN", "Main Hall", "Main Campus", 100);
  protected final MealOption option =
      new MealOption("LUNCH", "Lunch", null, MealOption.Category.LUNCH);

  protected DiningPlan draftPlan() {
    return new DiningPlan("FULL", 1, "Full Board", null, UUID.randomUUID(), START, END, MAKER);
  }

  protected DiningPlan activePlan() {
    DiningPlan plan = draftPlan();
    plan.transition(DiningPlan.Status.ACTIVE, CHECKER, "Plan independently checked", NOW, 0);
    return plan;
  }

  protected StudentDiningAssignment assignment() {
    return new StudentDiningAssignment(
        "DINE-1",
        STUDENT,
        "R260001",
        "Example Student",
        PERIOD,
        "2026-S2",
        "CSC",
        null,
        hall,
        activePlan(),
        null,
        START,
        END,
        MAKER);
  }

  protected MealServiceSession plannedSession() {
    return new MealServiceSession(
        "MEAL-1", hall, option, START, NOW, NOW.plusSeconds(3600), 100, MAKER);
  }

  protected MealServiceSession openSession() {
    var session = plannedSession();
    session.open(ATTENDANT, NOW, 0);
    return session;
  }

  protected void deactivateHall() {
    hall.update("MAIN", "Main Hall", "Main Campus", 100, false, 0);
  }

  protected void deactivateOption() {
    option.update("LUNCH", "Lunch", null, MealOption.Category.LUNCH, false, 0);
  }
}
