package zw.ac.uz.emhare.accommodation.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.accommodation.setup.domain.model.*;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod.Status;

/**
 * @author Tinashe K
 */
class AccommodationSetupGovernanceTest extends AccommodationGovernanceFixture {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "academic-period",
        "opens",
        "closes",
        "occupancy-start",
        "occupancy-end",
        "cutoff",
        "preparer",
        "same-window",
        "reversed-occupancy",
        "early-cutoff"
      })
  void accommodationPeriodsRejectMissingOrInconsistentApplicationAndOccupancyDates(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccommodationApplicationPeriod(
                invalid.equals("academic-period") ? null : PERIOD,
                "2026-S2",
                "SEP",
                "September",
                invalid.equals("opens") ? null : NOW,
                invalid.equals("closes")
                    ? null
                    : invalid.equals("same-window") ? NOW : NOW.plusSeconds(3600),
                invalid.equals("occupancy-start") ? null : START,
                invalid.equals("occupancy-end")
                    ? null
                    : invalid.equals("reversed-occupancy") ? START.minusDays(1) : END,
                invalid.equals("cutoff")
                    ? null
                    : invalid.equals("early-cutoff") ? NOW : NOW.plusSeconds(7200),
                invalid.equals("preparer") ? null : MAKER));
  }

  @Test
  void onlyDraftPeriodCanBeCorrectedBeforeIndependentlyApprovedForwardLifecycle() {
    var period = draftPeriod();
    assertThrows(
        IllegalStateException.class,
        () ->
            period.updateDraft(
                PERIOD,
                "2026-S2",
                "SEP",
                "September revised",
                NOW,
                NOW.plusSeconds(3600),
                START,
                END,
                NOW.plusSeconds(3600),
                1));
    period.updateDraft(
        PERIOD,
        "2026-s2",
        "sep",
        " September revised ",
        NOW,
        NOW.plusSeconds(3600),
        START,
        START,
        NOW.plusSeconds(3600),
        0);
    assertEquals("September revised", period.getName());
    assertEquals("SEP", period.getCode());
    assertEquals(START, period.getOccupancyEndsOn());
    assertThrows(
        IllegalArgumentException.class,
        () -> period.transition(Status.APPLICATION_OPEN, MAKER, "Self approval", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> period.transition(Status.APPLICATION_OPEN, null, "No actor", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.APPLICATION_OPEN, CHECKER, "Stale", NOW, 1));
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.CLOSED, CHECKER, "Skip phases", NOW, 0));
    period.transition(Status.APPLICATION_OPEN, CHECKER, " Approved opening ", NOW, 0);
    assertEquals(CHECKER, period.getApprovedByUserId());
    assertEquals(NOW, period.getApprovedAt());
    assertEquals("Approved opening", period.getApprovalReason());
    assertThrows(
        IllegalStateException.class,
        () ->
            period.updateDraft(
                PERIOD,
                "2026-S2",
                "NEW",
                "Mutation",
                NOW,
                NOW.plusSeconds(3600),
                START,
                END,
                NOW.plusSeconds(7200),
                0));
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.CLOSED, CHECKER, "Skip closure", NOW, 0));
    period.transition(Status.APPLICATION_CLOSED, CHECKER, "Applications closed", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.APPLICATION_OPEN, CHECKER, "Reverse workflow", NOW, 0));
    period.transition(Status.ALLOCATION_ACTIVE, CHECKER, "Allocate eligible students", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.APPLICATION_CLOSED, CHECKER, "Reverse workflow", NOW, 0));
    period.transition(Status.CLOSED, CHECKER, "Period reconciled", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> period.transition(Status.APPLICATION_OPEN, CHECKER, "Reopen history", NOW, 0));
    assertEquals(Status.CLOSED, period.getStatus());
  }

  @ParameterizedTest
  @ValueSource(strings = {"premise", "inactive-premise"})
  void residenceHallCannotBelongToMissingOrInactivePremise(String invalid) {
    if (invalid.equals("inactive-premise"))
      premise.update("MAIN", "Campus", "Address", null, null, null, false, 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResidenceHall(
                invalid.equals("premise") ? null : premise, "NC", "New Complex", null, null, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"hall", "inactive-hall", "room-type", "inactive-room-type", "capacity"})
  void roomRequiresActiveHallTypeAndPositiveOccupancyCapacity(String invalid) {
    if (invalid.equals("inactive-hall"))
      hall.update(premise, "NC", "New Complex", null, null, null, false, 0);
    if (invalid.equals("inactive-room-type"))
      roomType.update("DOUBLE", "Double", null, 2, false, 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccommodationRoom(
                invalid.equals("hall") ? null : hall,
                invalid.equals("room-type") ? null : roomType,
                "101",
                null,
                invalid.equals("capacity") ? 0 : 2,
                false,
                null,
                null,
                null));
  }

  @Test
  void setupCorrectionsAreVersionedAndDefaultPoliciesRemainExplicit() {
    var defaultHall = new ResidenceHall(premise, "NC2", "Annex", null, null, null);
    var defaultRoom =
        new AccommodationRoom(defaultHall, roomType, "101", null, 2, false, null, null, null);
    assertEquals(ResidenceHall.ResidentGenderPolicy.ANY, defaultHall.getResidentGenderPolicy());
    assertEquals(AccommodationRoom.ConditionStatus.AVAILABLE, defaultRoom.getConditionStatus());
    assertThrows(
        IllegalStateException.class,
        () -> premise.update("NEW", "New", "Address", null, null, null, false, 1));
    assertThrows(
        IllegalStateException.class,
        () -> hall.update(premise, "NEW", "New", null, null, null, false, 1));
    assertThrows(
        IllegalStateException.class, () -> roomType.update("NEW", "New", null, 1, false, 1));
    assertThrows(
        IllegalStateException.class,
        () -> room.update(hall, roomType, "NEW", null, 1, false, null, null, null, false, 1));
    UUID reservedGroup = UUID.randomUUID();
    room.update(
        hall,
        roomType,
        " nc102 ",
        " Ground ",
        1,
        true,
        AccommodationRoom.ConditionStatus.MAINTENANCE,
        " Plumbing repair ",
        reservedGroup,
        false,
        0);
    assertEquals("NC102", room.getCode());
    assertEquals("Ground", room.getFloorLabel());
    assertEquals(1, room.getCapacity());
    assertEquals(reservedGroup, room.getReservedForGroupId());
    assertEquals("Plumbing repair", room.getConditionNotes());
    assertFalse(room.isActive());
    hall.update(
        premise,
        "NC",
        "New Complex",
        ResidenceHall.ResidentGenderPolicy.FEMALE,
        " Warden ",
        " warden@example.test ",
        false,
        0);
    assertEquals(ResidenceHall.ResidentGenderPolicy.FEMALE, hall.getResidentGenderPolicy());
    assertEquals("Warden", hall.getWardenName());
    assertFalse(hall.isActive());
    roomType.update(" single ", " Single ", " One resident ", 1, false, 0);
    assertEquals("SINGLE", roomType.getCode());
    assertEquals("One resident", roomType.getDescription());
    assertFalse(roomType.isActive());
    premise.update(
        " leased ",
        " Leased Premise ",
        " City Centre ",
        " Harare ",
        " Landlord ",
        " Contact ",
        false,
        0);
    assertEquals("LEASED", premise.getCode());
    assertEquals("Harare", premise.getSuburb());
    assertEquals("Landlord", premise.getLandlordName());
    assertEquals("Contact", premise.getContactDetails());
    assertFalse(premise.isActive());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void requiredSetupCodesCannotBeBlankAndOptionalPremiseMetadataStaysAbsent(String value) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccommodationPremise(value, "Campus", "Address", null, null, null));
    var premise = new AccommodationPremise("MAIN", "Campus", "Address", value, value, value);
    assertNull(premise.getSuburb());
    assertNull(premise.getLandlordName());
    assertNull(premise.getContactDetails());
  }

  @Test
  void roomTypeCannotHaveZeroDefaultCapacity() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccommodationRoomType("SINGLE", "Single", null, 0));
  }
}
