package zw.ac.uz.emhare.accommodation.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.accommodation.operations.domain.model.*;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocation.Status;

/**
 * @author Tinashe K
 */
class RoomAllocationGovernanceTest extends AccommodationGovernanceFixture {
  @ParameterizedTest
  @ValueSource(
      strings = {"application", "room", "rate", "from", "until", "actor", "time", "reversed"})
  void allocationRequiresCompleteOwnedEvidenceAndOrderedOccupancyDates(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RoomAllocation(
                "ALLOC-1",
                invalid.equals("application") ? null : eligibleApplication(),
                invalid.equals("room") ? null : room,
                invalid.equals("rate") ? null : activeRate(),
                invalid.equals("from") ? null : START,
                invalid.equals("until")
                    ? null
                    : invalid.equals("reversed") ? START.minusDays(1) : END,
                invalid.equals("actor") ? null : MAKER,
                invalid.equals("time") ? null : NOW));
  }

  @Test
  void submittedApplicationsAndDraftRatesCannotBeAllocatedButWaitlistedStudentsCan() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new RoomAllocation(
                "ALLOC-1", application(), room, activeRate(), START, END, MAKER, NOW));
    assertThrows(
        IllegalStateException.class,
        () ->
            new RoomAllocation(
                "ALLOC-1", eligibleApplication(), room, draftRate(), START, END, MAKER, NOW));
    var waitlisted = application();
    waitlisted.evaluate(
        AccommodationApplication.Status.WAITLISTED, 10, null, "Awaiting room", CHECKER, NOW, 0);
    var proposal =
        new RoomAllocation("ALLOC-2", waitlisted, room, activeRate(), START, START, MAKER, NOW);
    assertEquals(Status.PROPOSED, proposal.getStatus());
    assertEquals(START, proposal.getOccupancyEndsOn());
  }

  @Test
  void allocationApprovalCheckInAndIndependentCheckOutRetainEvidence() {
    var allocation = proposedAllocation();
    assertEquals(RoomAllocation.BillingStatus.NOT_REQUESTED, allocation.getBillingStatus());
    assertNull(allocation.getBillingEventId());
    assertEquals(
        Status.PROPOSED,
        allocation.approve(CHECKER, " Independently verified allocation ", NOW, 0));
    assertEquals(CHECKER, allocation.getApprovedByUserId());
    assertEquals(NOW, allocation.getApprovedAt());
    assertEquals("Independently verified allocation", allocation.getApprovalReason());
    assertEquals(
        Status.ALLOCATED,
        allocation.checkIn(WARDEN, " Key and room inventory issued ", NOW.plusSeconds(60), 0));
    assertEquals(WARDEN, allocation.getCheckedInByUserId());
    assertEquals(NOW.plusSeconds(60), allocation.getCheckedInAt());
    assertEquals("Key and room inventory issued", allocation.getCheckInNotes());
    assertEquals(
        Status.CHECKED_IN,
        allocation.checkOut(
            CHECKER, " Room and key independently reconciled ", NOW.plusSeconds(120), 0));
    assertEquals(Status.CHECKED_OUT, allocation.getStatus());
    assertEquals(CHECKER, allocation.getCheckedOutByUserId());
    assertEquals(NOW.plusSeconds(120), allocation.getCheckedOutAt());
    assertEquals("Room and key independently reconciled", allocation.getCheckOutNotes());
    assertEquals(MAKER, allocation.getAllocatedByUserId());
    assertEquals(NOW, allocation.getAllocatedAt());
  }

  @Test
  void allocationCannotSkipTransitionsOrReuseApprovalAndCheckOutActors() {
    var allocation = proposedAllocation();
    assertThrows(
        IllegalStateException.class, () -> allocation.checkIn(WARDEN, "Not approved", NOW, 0));
    assertThrows(
        IllegalStateException.class, () -> allocation.checkOut(CHECKER, "Not checked in", NOW, 0));
    assertThrows(
        IllegalArgumentException.class, () -> allocation.approve(null, "No operator", NOW, 0));
    assertThrows(
        IllegalArgumentException.class, () -> allocation.approve(MAKER, "Self approval", NOW, 0));
    allocation.approve(CHECKER, "Approved", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> allocation.approve(CHECKER, "Repeated approval", NOW, 0));
    allocation.checkIn(WARDEN, "Issued keys", NOW, 0);
    assertThrows(
        IllegalArgumentException.class, () -> allocation.checkOut(null, "No operator", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> allocation.checkOut(WARDEN, "Self check-out", NOW, 0));
    assertEquals(Status.CHECKED_IN, allocation.getStatus());
  }

  @ParameterizedTest
  @ValueSource(strings = {"approve", "check-in", "check-out", "cancel"})
  void staleAllocationDecisionCannotAdvanceItsLifecycle(String action) {
    var allocation = proposedAllocation();
    if (action.equals("check-in") || action.equals("check-out"))
      allocation.approve(CHECKER, "Approved", NOW, 0);
    if (action.equals("check-out")) allocation.checkIn(WARDEN, "Checked in", NOW, 0);
    Status initial = allocation.getStatus();
    assertThrows(
        IllegalStateException.class,
        () -> {
          switch (action) {
            case "approve" -> allocation.approve(CHECKER, "Stale", NOW, 1);
            case "check-in" -> allocation.checkIn(WARDEN, "Stale", NOW, 1);
            case "check-out" -> allocation.checkOut(CHECKER, "Stale", NOW, 1);
            case "cancel" -> allocation.end(Status.CANCELLED, CHECKER, "Stale", NOW, 1);
            default -> fail("Unknown action");
          }
        });
    assertEquals(initial, allocation.getStatus());
  }

  @Test
  void onlyProposalsCanCancelAndOnlyApprovedOrOccupiedRoomsCanWithdraw() {
    var proposal = proposedAllocation();
    assertThrows(
        IllegalArgumentException.class,
        () -> proposal.end(Status.CHECKED_OUT, CHECKER, "Invalid terminal target", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> proposal.end(Status.WITHDRAWN, CHECKER, "Not approved", NOW, 0));
    assertEquals(
        Status.PROPOSED,
        proposal.end(Status.CANCELLED, CHECKER, " Cancel unused proposal ", NOW, 0));
    assertEquals(Status.CANCELLED, proposal.getStatus());
    assertEquals(CHECKER, proposal.getEndedByUserId());
    assertEquals(NOW, proposal.getEndedAt());
    assertEquals("Cancel unused proposal", proposal.getEndReason());
    var allocated = proposedAllocation();
    allocated.approve(CHECKER, "Approved", NOW, 0);
    assertThrows(
        IllegalStateException.class,
        () -> allocated.end(Status.CANCELLED, CHECKER, "Too late", NOW, 0));
    assertEquals(
        Status.ALLOCATED, allocated.end(Status.WITHDRAWN, CHECKER, "Student withdrew", NOW, 0));
    var occupied = proposedAllocation();
    occupied.approve(CHECKER, "Approved", NOW, 0);
    occupied.checkIn(WARDEN, "Checked in", NOW, 0);
    assertEquals(
        Status.CHECKED_IN, occupied.end(Status.WITHDRAWN, CHECKER, "Emergency withdrawal", NOW, 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"allocation", "new-status", "type", "actor", "time"})
  void allocationAuditEventsRequireOwnedAggregateAndDecisionEvidence(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RoomAllocationEvent(
                invalid.equals("allocation") ? null : proposedAllocation(),
                null,
                invalid.equals("new-status") ? null : Status.PROPOSED,
                invalid.equals("type") ? null : RoomAllocationEvent.EventType.PROPOSED,
                null,
                room,
                "Proposed allocation",
                invalid.equals("actor") ? null : MAKER,
                invalid.equals("time") ? null : NOW));
  }

  @Test
  void auditEventKeepsFromAndToRoomEvidenceAlongsideTheStateTransition() {
    var allocation = proposedAllocation();
    Status before = allocation.approve(CHECKER, "Approved", NOW, 0);
    var event =
        new RoomAllocationEvent(
            allocation,
            before,
            allocation.getStatus(),
            RoomAllocationEvent.EventType.APPROVED,
            room,
            room,
            " Approved allocation ",
            CHECKER,
            NOW);
    assertSame(allocation, event.getAllocation());
    assertEquals(Status.PROPOSED, event.getPreviousStatus());
    assertEquals(Status.ALLOCATED, event.getNewStatus());
    assertSame(room, event.getFromRoom());
    assertSame(room, event.getToRoom());
    assertEquals(CHECKER, event.getActorUserId());
    assertEquals("Approved allocation", event.getReason());
    assertEquals(NOW, event.getOccurredAt());
  }
}
