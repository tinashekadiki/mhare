package zw.ac.uz.emhare.accommodation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import zw.ac.uz.emhare.accommodation.operations.domain.model.*;
import zw.ac.uz.emhare.accommodation.setup.domain.model.*;

/**
 * Real accommodation evidence with separate preparation and approval operators. @author Tinashe K
 */
abstract class AccommodationGovernanceFixture {
  protected static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  protected static final LocalDate START = LocalDate.of(2026, 9, 1);
  protected static final LocalDate END = LocalDate.of(2026, 12, 31);
  protected static final UUID MAKER = UUID.randomUUID();
  protected static final UUID CHECKER = UUID.randomUUID();
  protected static final UUID WARDEN = UUID.randomUUID();
  protected static final UUID STUDENT = UUID.randomUUID();
  protected static final UUID PROGRAMME = UUID.randomUUID();
  protected static final UUID PERIOD = UUID.randomUUID();
  protected final AccommodationPremise premise =
      new AccommodationPremise("MAIN", "Main Campus", "Mount Pleasant", null, null, null);
  protected final ResidenceHall hall =
      new ResidenceHall(
          premise, "NC", "New Complex", ResidenceHall.ResidentGenderPolicy.ANY, null, null);
  protected final AccommodationRoomType roomType =
      new AccommodationRoomType("DOUBLE", "Double Room", null, 2);
  protected final AccommodationRoom room =
      new AccommodationRoom(
          hall,
          roomType,
          "NC101",
          "1",
          2,
          true,
          AccommodationRoom.ConditionStatus.AVAILABLE,
          null,
          null);

  protected AccommodationApplicationPeriod draftPeriod() {
    return new AccommodationApplicationPeriod(
        PERIOD,
        "2026-S2",
        "SEP",
        "September",
        NOW.minusSeconds(3600),
        NOW.plusSeconds(3600),
        START,
        END,
        NOW.plusSeconds(7200),
        MAKER);
  }

  protected AccommodationApplicationPeriod openPeriod() {
    var period = draftPeriod();
    period.transition(
        AccommodationApplicationPeriod.Status.APPLICATION_OPEN,
        CHECKER,
        "Approved application window",
        NOW,
        0);
    return period;
  }

  protected AccommodationApplication application() {
    return application(openPeriod(), NOW);
  }

  protected AccommodationApplication application(
      AccommodationApplicationPeriod period, Instant submittedAt) {
    return new AccommodationApplication(
        "ACC-1",
        period,
        STUDENT,
        "R260001",
        "Example Student",
        "student@example.test",
        "FEMALE",
        null,
        "ZWE",
        null,
        PROGRAMME,
        "CSC",
        "Computing",
        1,
        null,
        AccommodationApplication.PaymentState.PAID,
        roomType,
        null,
        submittedAt);
  }

  protected AccommodationApplication eligibleApplication() {
    var application = application();
    application.evaluate(
        AccommodationApplication.Status.ELIGIBLE,
        10,
        null,
        "Verified eligibility",
        CHECKER,
        NOW,
        0);
    return application;
  }

  protected AccommodationRate draftRate() {
    return new AccommodationRate(
        openPeriod(),
        roomType,
        1,
        UUID.randomUUID(),
        "USD",
        new BigDecimal("100"),
        null,
        null,
        NOW,
        null,
        MAKER);
  }

  protected AccommodationRate activeRate() {
    var rate = draftRate();
    rate.transition(AccommodationRate.Status.ACTIVE, CHECKER, "Verified Finance rate", NOW, 0);
    return rate;
  }

  protected RoomAllocation proposedAllocation() {
    return new RoomAllocation(
        "ALLOC-1", eligibleApplication(), room, activeRate(), START, END, MAKER, NOW);
  }
}
