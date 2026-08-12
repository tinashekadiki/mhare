package zw.ac.uz.emhare.accommodation.operations;

import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationApplication;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationRate;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationWaitlistEntry;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocation;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationPremise;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoomType;
import zw.ac.uz.emhare.accommodation.setup.domain.model.ResidenceHall;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.accommodation.setup.*;

/** @author Tinashe K */
class AccommodationOperationsDomainTest {
    private static final UUID PREPARER = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID APPROVER = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID CHECK_IN_OPERATOR = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID CHECK_OUT_OPERATOR = UUID.fromString("20000000-0000-4000-8000-000000000004");
    private static final Instant APPLICATION_OPEN = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant APPLICATION_CLOSE = Instant.parse("2027-01-31T23:59:59Z");
    private static final Instant ALLOCATION_CUTOFF = Instant.parse("2027-02-15T23:59:59Z");

    @Test
    void ratesUsdAtParWithoutInventingAnExchangeRate() {
        Fixture fixture = fixture();
        AccommodationRate rate = new AccommodationRate(fixture.period, fixture.roomType, 1,
                UUID.randomUUID(), "USD", new BigDecimal("950.00"), null, null,
                Instant.parse("2026-12-01T00:00:00Z"), null, PREPARER);

        assertEquals(AccommodationRate.RatingStatus.RATED, rate.getRatingStatus());
        assertEquals(new BigDecimal("950.00"), rate.getIndicativeBaseAmount());
        assertNull(rate.getExchangeRateId());
    }

    @Test
    void leavesForeignCurrencyUnratedAndBlocksActivationWithoutAnEffectiveRate() {
        Fixture fixture = fixture();
        AccommodationRate rate = new AccommodationRate(fixture.period, fixture.roomType, 1,
                UUID.randomUUID(), "ZWG", new BigDecimal("12000.00"), null, null,
                Instant.parse("2026-12-01T00:00:00Z"), null, PREPARER);

        assertEquals(AccommodationRate.RatingStatus.UNRATED, rate.getRatingStatus());
        assertNull(rate.getIndicativeBaseAmount());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> rate.transition(AccommodationRate.Status.ACTIVE, APPROVER,
                        "Approve residence rate", Instant.parse("2026-12-02T00:00:00Z"), 0));
        assertTrue(error.getMessage().contains("unrated"));
    }

    @Test
    void enforcesMakerCheckerAcrossAllocationAndOccupancyLifecycle() {
        Fixture fixture = fixture();
        AccommodationRate rate = activeUsdRate(fixture);
        AccommodationApplication application = eligibleApplication(fixture);
        RoomAllocation allocation = new RoomAllocation("ALL-00000001", application, fixture.room,
                rate, LocalDate.parse("2027-02-01"), LocalDate.parse("2027-06-30"),
                PREPARER, Instant.parse("2027-02-01T08:00:00Z"));

        assertThrows(IllegalArgumentException.class, () -> allocation.approve(PREPARER,
                "Self approve", Instant.parse("2027-02-01T09:00:00Z"), 0));
        allocation.approve(APPROVER, "Capacity and eligibility verified",
                Instant.parse("2027-02-01T09:00:00Z"), 0);
        application.markAllocated();
        allocation.checkIn(CHECK_IN_OPERATOR, "Identity and room inventory verified",
                Instant.parse("2027-02-01T10:00:00Z"), 0);

        assertThrows(IllegalArgumentException.class, () -> allocation.checkOut(CHECK_IN_OPERATOR,
                "Same operator attempted checkout", Instant.parse("2027-06-30T08:00:00Z"), 0));
        allocation.checkOut(CHECK_OUT_OPERATOR, "Keys returned and inspection complete",
                Instant.parse("2027-06-30T09:00:00Z"), 0);
        assertEquals(RoomAllocation.Status.CHECKED_OUT, allocation.getStatus());
    }

    @Test
    void onlyCreatesWaitlistEvidenceForAnEvaluatedWaitlistedApplication() {
        Fixture fixture = fixture();
        AccommodationApplication application = submittedApplication(fixture);
        application.evaluate(AccommodationApplication.Status.WAITLISTED, 85, null,
                "Eligible but current capacity is exhausted", APPROVER,
                Instant.parse("2027-02-01T08:00:00Z"), 0);

        AccommodationWaitlistEntry entry = new AccommodationWaitlistEntry(application, 1,
                APPROVER, Instant.parse("2027-02-01T08:00:00Z"));
        assertEquals(1, entry.getWaitlistPosition());
        assertEquals(85, entry.getPriorityScore());
        assertEquals(AccommodationWaitlistEntry.Status.ACTIVE, entry.getStatus());
    }

    private AccommodationRate activeUsdRate(Fixture fixture) {
        AccommodationRate rate = new AccommodationRate(fixture.period, fixture.roomType, 1,
                UUID.randomUUID(), "USD", new BigDecimal("950.00"), null, null,
                Instant.parse("2026-12-01T00:00:00Z"), null, PREPARER);
        rate.transition(AccommodationRate.Status.ACTIVE, APPROVER, "Finance fee and amount verified",
                Instant.parse("2026-12-02T00:00:00Z"), 0);
        return rate;
    }

    private AccommodationApplication eligibleApplication(Fixture fixture) {
        AccommodationApplication application = submittedApplication(fixture);
        application.evaluate(AccommodationApplication.Status.ELIGIBLE, 80, null,
                "Eligibility requirements satisfied", APPROVER,
                Instant.parse("2027-02-01T08:00:00Z"), 0);
        return application;
    }

    private AccommodationApplication submittedApplication(Fixture fixture) {
        return new AccommodationApplication("ACC-00000001", fixture.period, UUID.randomUUID(),
                "R271234A", "Example Student", "student@example.test", "FEMALE", null,
                "ZWE", "HARARE", UUID.randomUUID(), "BSC-CS", "BSc Computer Science", 1,
                null, AccommodationApplication.PaymentState.PAID, fixture.roomType, null,
                Instant.parse("2027-01-10T08:00:00Z"));
    }

    private Fixture fixture() {
        AccommodationApplicationPeriod period = new AccommodationApplicationPeriod(UUID.randomUUID(),
                "2027-S1", "RES-2027-S1", "Semester 1 residences", APPLICATION_OPEN,
                APPLICATION_CLOSE, LocalDate.parse("2027-02-01"), LocalDate.parse("2027-06-30"),
                ALLOCATION_CUTOFF, PREPARER);
        period.transition(AccommodationApplicationPeriod.Status.APPLICATION_OPEN, APPROVER,
                "Inventory and controls verified", Instant.parse("2026-12-01T08:00:00Z"), 0);
        AccommodationPremise premise = new AccommodationPremise("UZ-MP", "Mount Pleasant",
                "630 Churchill Avenue", "Mount Pleasant", null, null);
        AccommodationRoomType roomType = new AccommodationRoomType("SINGLE", "Single room", null, 1);
        ResidenceHall hall = new ResidenceHall(premise, "SWINTON", "Swinton Hall",
                ResidenceHall.ResidentGenderPolicy.FEMALE, null, null);
        AccommodationRoom room = new AccommodationRoom(hall, roomType, "A-101", "First", 1,
                false, AccommodationRoom.ConditionStatus.AVAILABLE, null, null);
        return new Fixture(period, roomType, room);
    }

    private record Fixture(AccommodationApplicationPeriod period, AccommodationRoomType roomType,
            AccommodationRoom room) {}
}
