package zw.ac.uz.emhare.dining.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.dining.operations.domain.model.*;

/**
 * @author Tinashe K
 */
class DiningMealEvidenceGovernanceTest extends DiningGovernanceFixture {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "hall",
        "inactive-hall",
        "option",
        "inactive-option",
        "date",
        "opens",
        "closes",
        "maker",
        "equal-window",
        "reversed-window",
        "negative-expected"
      })
  void mealSessionsRejectIncompleteSetupAndInvalidServiceWindows(String invalid) {
    if (invalid.equals("inactive-hall")) deactivateHall();
    if (invalid.equals("inactive-option")) deactivateOption();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MealServiceSession(
                "SESSION-1",
                invalid.equals("hall") ? null : hall,
                invalid.equals("option") ? null : option,
                invalid.equals("date") ? null : START,
                invalid.equals("opens") ? null : NOW,
                invalid.equals("closes")
                    ? null
                    : invalid.equals("equal-window")
                        ? NOW
                        : invalid.equals("reversed-window")
                            ? NOW.minusSeconds(1)
                            : NOW.plusSeconds(3600),
                invalid.equals("negative-expected") ? -1 : 0,
                invalid.equals("maker") ? null : MAKER));
  }

  @Test
  void sessionMayHaveUnknownExpectedServingsButReconciliationRequiresIndependentCount() {
    var session =
        new MealServiceSession(
            "SESSION-1", hall, option, START, NOW, NOW.plusSeconds(3600), null, MAKER);
    assertNull(session.getExpectedServings());
    assertThrows(IllegalStateException.class, () -> session.close(ATTENDANT, NOW, 0));
    assertThrows(
        IllegalStateException.class, () -> session.reconcile(CHECKER, 0, "Too early", NOW, 0));
    assertThrows(IllegalArgumentException.class, () -> session.open(null, NOW, 0));
    assertThrows(IllegalArgumentException.class, () -> session.open(MAKER, NOW, 0));
    session.open(ATTENDANT, NOW, 0);
    assertThrows(IllegalStateException.class, () -> session.open(CHECKER, NOW, 0));
    session.close(ATTENDANT, NOW.plusSeconds(3600), 0);
    assertThrows(
        IllegalArgumentException.class, () -> session.reconcile(null, 0, "No actor", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> session.reconcile(ATTENDANT, 0, "Self reconciliation", NOW, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> session.reconcile(CHECKER, -1, "Negative count", NOW, 0));
    session.reconcile(CHECKER, 0, " No meals issued ", NOW.plusSeconds(7200), 0);
    assertEquals(MealServiceSession.Status.RECONCILED, session.getStatus());
    assertEquals(0, session.getCountedServings());
    assertEquals(CHECKER, session.getReconciledByUserId());
    assertEquals("No meals issued", session.getReconciliationReason());
    assertEquals(NOW.plusSeconds(7200), session.getReconciledAt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"open", "close", "reconcile"})
  void staleMealSessionDecisionLeavesCurrentStateUnchanged(String action) {
    var session = plannedSession();
    if (!action.equals("open")) session.open(ATTENDANT, NOW, 0);
    if (action.equals("reconcile")) session.close(ATTENDANT, NOW, 0);
    var initial = session.getStatus();
    assertThrows(
        IllegalStateException.class,
        () -> {
          switch (action) {
            case "open" -> session.open(ATTENDANT, NOW, 1);
            case "close" -> session.close(ATTENDANT, NOW, 1);
            case "reconcile" -> session.reconcile(CHECKER, 0, "Counted", NOW, 1);
            default -> fail("Unknown action");
          }
        });
    assertEquals(initial, session.getStatus());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "session",
        "closed-session",
        "student",
        "outcome",
        "actor",
        "time",
        "channel",
        "missing-assignment",
        "denial-code-null",
        "denial-code-blank",
        "denial-reason-null",
        "denial-reason-blank"
      })
  void admissionEvidenceRequiresAnOpenSessionAndExplicitEntitlementOrDenial(String invalid) {
    var session = openSession();
    if (invalid.equals("closed-session")) session.close(ATTENDANT, NOW, 0);
    var assignment = assignment();
    assignment.activate(CHECKER, "Approved", NOW, 0);
    boolean denied = invalid.startsWith("denial");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MealAttendanceEvent(
                "MEAL-1",
                invalid.equals("session") ? null : session,
                invalid.equals("missing-assignment") ? null : assignment,
                invalid.equals("student") ? null : STUDENT,
                "R260001",
                "Student",
                invalid.equals("outcome")
                    ? null
                    : denied
                        ? MealAttendanceEvent.Outcome.DENIED
                        : MealAttendanceEvent.Outcome.ADMITTED,
                invalid.equals("denial-code-null")
                    ? null
                    : invalid.equals("denial-code-blank") ? " " : "NOT_ENTITLED",
                invalid.equals("denial-reason-null")
                    ? null
                    : invalid.equals("denial-reason-blank") ? " " : "No active entitlement",
                invalid.equals("actor") ? null : ATTENDANT,
                invalid.equals("time") ? null : NOW,
                invalid.equals("channel") ? null : MealAttendanceEvent.CaptureChannel.ONLINE,
                null,
                "scan-1"));
  }

  @Test
  void deniedAdmissionPreservesReasonAndReversalAddsEvidenceWithoutChangingOriginalOutcome() {
    var event =
        new MealAttendanceEvent(
            "meal-1",
            openSession(),
            null,
            STUDENT,
            "r260001",
            " Student ",
            MealAttendanceEvent.Outcome.DENIED,
            " no_plan ",
            " Student has no active plan ",
            ATTENDANT,
            NOW,
            MealAttendanceEvent.CaptureChannel.OFFLINE_SYNC,
            " gate-1 ",
            " scan-1 ");
    assertEquals("NO_PLAN", event.getDenialReasonCode());
    assertEquals("Student has no active plan", event.getDenialReason());
    assertEquals("gate-1", event.getDeviceId());
    assertEquals("scan-1", event.getIdempotencyKey());
    assertNull(event.getAssignment());
    var reversal =
        new MealAttendanceReversal(
            event, " correction ", " Wrong student scanned ", CHECKER, NOW.plusSeconds(60));
    assertSame(event, reversal.getEvent());
    assertEquals("CORRECTION", reversal.getReasonCode());
    assertEquals("Wrong student scanned", reversal.getReason());
    assertEquals(CHECKER, reversal.getReversedByUserId());
    assertEquals(NOW.plusSeconds(60), reversal.getReversedAt());
    assertEquals(MealAttendanceEvent.Outcome.DENIED, event.getOutcome());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void captureRequiresIdempotencyEvidenceAndMissingDeviceIsNotInvented(String value) {
    var session = openSession();
    var assignment = assignment();
    assignment.activate(CHECKER, "Approved", NOW, 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MealAttendanceEvent(
                "MEAL-1",
                session,
                assignment,
                STUDENT,
                "R260001",
                "Student",
                MealAttendanceEvent.Outcome.ADMITTED,
                null,
                null,
                ATTENDANT,
                NOW,
                MealAttendanceEvent.CaptureChannel.ONLINE,
                value,
                value));
    var admitted =
        new MealAttendanceEvent(
            "MEAL-1",
            session,
            assignment,
            STUDENT,
            "R260001",
            "Student",
            MealAttendanceEvent.Outcome.ADMITTED,
            "IGNORED",
            "Not a denial",
            ATTENDANT,
            NOW,
            MealAttendanceEvent.CaptureChannel.ONLINE,
            value,
            "scan-1");
    assertNull(admitted.getDeviceId());
    assertNull(admitted.getDenialReasonCode());
    assertNull(admitted.getDenialReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"event", "actor", "time"})
  void reversalRequiresOriginalEvidenceAndResponsibleOperator(String invalid) {
    var original =
        new MealAttendanceEvent(
            "MEAL-1",
            openSession(),
            null,
            STUDENT,
            "R260001",
            "Student",
            MealAttendanceEvent.Outcome.DENIED,
            "NO_PLAN",
            "No active plan",
            ATTENDANT,
            NOW,
            MealAttendanceEvent.CaptureChannel.ONLINE,
            null,
            "scan-1");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MealAttendanceReversal(
                invalid.equals("event") ? null : original,
                "CORRECTION",
                "Wrong scan",
                invalid.equals("actor") ? null : CHECKER,
                invalid.equals("time") ? null : NOW));
  }

  @ParameterizedTest
  @ValueSource(strings = {"type", "aggregate", "new-state", "actor", "time"})
  void workflowAuditEvidenceRequiresOwnedAggregateStateAndActor(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DiningWorkflowEvent(
                invalid.equals("type") ? null : DiningWorkflowEvent.AggregateType.MEAL_SESSION,
                invalid.equals("aggregate") ? null : UUID.randomUUID(),
                "OPEN",
                invalid.equals("new-state") ? null : "CLOSED",
                "CLOSE",
                "Service completed",
                invalid.equals("actor") ? null : ATTENDANT,
                invalid.equals("time") ? null : NOW));
  }

  @Test
  void workflowEvidenceNormalizesStateAndRetainsExactAggregateReference() {
    UUID aggregate = UUID.randomUUID();
    var event =
        new DiningWorkflowEvent(
            DiningWorkflowEvent.AggregateType.MEAL_SESSION,
            aggregate,
            " ",
            " open ",
            " open ",
            " Service opened ",
            ATTENDANT,
            NOW);
    assertEquals(aggregate, event.getAggregateId());
    assertNull(event.getPreviousState());
    assertEquals("OPEN", event.getNewState());
    assertEquals("OPEN", event.getEventType());
    assertEquals("Service opened", event.getReason());
    assertEquals(ATTENDANT, event.getActorUserId());
  }
}
