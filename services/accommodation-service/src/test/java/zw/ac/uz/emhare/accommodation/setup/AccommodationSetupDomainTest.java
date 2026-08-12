package zw.ac.uz.emhare.accommodation.setup;

import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationPremise;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoomType;
import zw.ac.uz.emhare.accommodation.setup.domain.model.ResidenceHall;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class AccommodationSetupDomainTest {
    private static final UUID ACADEMIC_PERIOD_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID PREPARER_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID APPROVER_ID = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final Instant APPLICATIONS_OPEN = Instant.parse("2027-01-01T06:00:00Z");
    private static final Instant APPLICATIONS_CLOSE = Instant.parse("2027-01-31T15:00:00Z");
    private static final Instant ALLOCATION_CUTOFF = Instant.parse("2027-02-15T15:00:00Z");

    @Test
    void enforcesMakerCheckerAndOrderedApplicationPeriodTransitions() {
        AccommodationApplicationPeriod period = period();

        IllegalArgumentException sameOperator = assertThrows(IllegalArgumentException.class,
                () -> period.transition(AccommodationApplicationPeriod.Status.APPLICATION_OPEN,
                        PREPARER_ID, "Open applications", Instant.parse("2026-12-01T08:00:00Z"), 0));
        assertTrue(sameOperator.getMessage().contains("different authorised operator"));

        period.transition(AccommodationApplicationPeriod.Status.APPLICATION_OPEN, APPROVER_ID,
                "Capacity and rates reviewed", Instant.parse("2026-12-01T08:00:00Z"), 0);
        assertEquals(AccommodationApplicationPeriod.Status.APPLICATION_OPEN, period.getStatus());
        assertEquals(APPROVER_ID, period.getApprovedByUserId());

        IllegalStateException skippedStage = assertThrows(IllegalStateException.class,
                () -> period.transition(AccommodationApplicationPeriod.Status.ALLOCATION_ACTIVE,
                        APPROVER_ID, "Skip closure", Instant.parse("2027-02-01T08:00:00Z"), 0));
        assertTrue(skippedStage.getMessage().contains("cannot move"));
    }

    @Test
    void rejectsInvalidApplicationAndOccupancyWindows() {
        assertThrows(IllegalArgumentException.class, () -> new AccommodationApplicationPeriod(
                ACADEMIC_PERIOD_ID, "2027-S1", "RES-2027-S1", "Semester 1 residences",
                APPLICATIONS_CLOSE, APPLICATIONS_OPEN, LocalDate.parse("2027-02-01"),
                LocalDate.parse("2027-06-30"), ALLOCATION_CUTOFF, PREPARER_ID));

        assertThrows(IllegalArgumentException.class, () -> new AccommodationApplicationPeriod(
                ACADEMIC_PERIOD_ID, "2027-S1", "RES-2027-S1", "Semester 1 residences",
                APPLICATIONS_OPEN, APPLICATIONS_CLOSE, LocalDate.parse("2027-07-01"),
                LocalDate.parse("2027-06-30"), ALLOCATION_CUTOFF, PREPARER_ID));
    }

    @Test
    void normalizesInventoryCodesAndRejectsUnsafeCapacity() {
        AccommodationPremise premise = new AccommodationPremise("  mt-pleasant ", "Mount Pleasant",
                "630 Churchill Avenue", "Mount Pleasant", null, "residences@example.test");
        AccommodationRoomType roomType = new AccommodationRoomType(" single ", "Single room", null, 1);
        ResidenceHall hall = new ResidenceHall(premise, "  swinton ", "Swinton Hall",
                ResidenceHall.ResidentGenderPolicy.FEMALE, "Residence Warden", "+263 000 000");

        AccommodationRoom room = new AccommodationRoom(hall, roomType, " a-101 ", "First", 1,
                true, AccommodationRoom.ConditionStatus.AVAILABLE, null, null);

        assertEquals("MT-PLEASANT", premise.getCode());
        assertEquals("SINGLE", roomType.getCode());
        assertEquals("SWINTON", hall.getCode());
        assertEquals("A-101", room.getCode());
        assertThrows(IllegalArgumentException.class, () -> new AccommodationRoom(hall, roomType,
                "A-102", "First", 0, false, AccommodationRoom.ConditionStatus.AVAILABLE, null, null));
    }

    @Test
    void preventsEditingAnApprovedApplicationPeriod() {
        AccommodationApplicationPeriod period = period();
        period.transition(AccommodationApplicationPeriod.Status.APPLICATION_OPEN, APPROVER_ID,
                "Capacity and rates reviewed", Instant.parse("2026-12-01T08:00:00Z"), 0);

        assertThrows(IllegalStateException.class, () -> period.updateDraft(ACADEMIC_PERIOD_ID,
                "2027-S1", "RES-2027-S1", "Changed name", APPLICATIONS_OPEN, APPLICATIONS_CLOSE,
                LocalDate.parse("2027-02-01"), LocalDate.parse("2027-06-30"), ALLOCATION_CUTOFF, 0));
    }

    private AccommodationApplicationPeriod period() {
        return new AccommodationApplicationPeriod(ACADEMIC_PERIOD_ID, "2027-S1", "RES-2027-S1",
                "Semester 1 residences", APPLICATIONS_OPEN, APPLICATIONS_CLOSE,
                LocalDate.parse("2027-02-01"), LocalDate.parse("2027-06-30"),
                ALLOCATION_CUTOFF, PREPARER_ID);
    }
}
