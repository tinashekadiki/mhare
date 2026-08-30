package zw.ac.uz.emhare.dining.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDiningAssignment;
import zw.ac.uz.emhare.dining.operations.domain.model.StudentDiningAssignment.Status;
import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlan;

/**
 * @author Tinashe K
 */
class DiningAssignmentGovernanceTest extends DiningGovernanceFixture {
  @Test
  void assignmentActivationSuspensionResumptionAndEndRetainIndependentAuditEvidence() {
    var assignment = assignment();
    assertEquals(
        StudentDiningAssignment.BillingStatus.NOT_REQUESTED, assignment.getBillingStatus());
    assertNull(assignment.getBillingEventId());
    assignment.activate(CHECKER, " Approved eligible student ", NOW, 0);
    assertEquals(CHECKER, assignment.getApprovedByUserId());
    assertEquals(NOW, assignment.getApprovedAt());
    assignment.suspend(" Service temporarily suspended ", 0);
    assertEquals(Status.SUSPENDED, assignment.getStatus());
    assertEquals("Service temporarily suspended", assignment.getApprovalReason());
    assignment.resume(" Resume approved entitlement ", 0);
    assertEquals(Status.ACTIVE, assignment.getStatus());
    assignment.end(Status.ENDED, CHECKER, " Student entitlement ended ", NOW.plusSeconds(60), 0);
    assertEquals(Status.ENDED, assignment.getStatus());
    assertEquals(CHECKER, assignment.getEndedByUserId());
    assertEquals(NOW.plusSeconds(60), assignment.getEndedAt());
    assertEquals("Student entitlement ended", assignment.getEndReason());
    assertEquals(PERIOD, assignment.getAcademicPeriodId());
    assertEquals(STUDENT, assignment.getStudentId());
  }

  @Test
  void draftCanBeCancelledAndSuspendedAssignmentCanEnd() {
    var draft = assignment();
    draft.end(Status.CANCELLED, CHECKER, "Student withdrew before activation", NOW, 0);
    assertEquals(Status.CANCELLED, draft.getStatus());
    var suspended = assignment();
    suspended.activate(CHECKER, "Approved", NOW, 0);
    suspended.suspend("Suspended", 0);
    suspended.end(Status.ENDED, CHECKER, "Permanently ended", NOW, 0);
    assertEquals(Status.ENDED, suspended.getStatus());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "student",
        "period",
        "hall",
        "inactive-hall",
        "plan",
        "draft-plan",
        "from",
        "until",
        "maker",
        "reversed",
        "before-plan",
        "after-plan"
      })
  void assignmentsRequireActiveOwnedSetupAndContainedDates(String invalid) {
    DiningPlan plan = "draft-plan".equals(invalid) ? draftPlan() : activePlan();
    if (invalid.equals("inactive-hall")) deactivateHall();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StudentDiningAssignment(
                "DINE-1",
                invalid.equals("student") ? null : STUDENT,
                "R260001",
                "Student",
                invalid.equals("period") ? null : PERIOD,
                "2026-S2",
                "CSC",
                null,
                invalid.equals("hall") ? null : hall,
                invalid.equals("plan") ? null : plan,
                null,
                invalid.equals("from")
                    ? null
                    : invalid.equals("before-plan") ? START.minusDays(1) : START,
                invalid.equals("until")
                    ? null
                    : invalid.equals("reversed")
                        ? START.minusDays(1)
                        : invalid.equals("after-plan") ? END.plusDays(1) : END,
                invalid.equals("maker") ? null : MAKER));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " group-a "})
  void optionalStudentGroupIsNormalizedAndUnboundedPlanPermitsLaterAssignment(String group) {
    DiningPlan plan = new DiningPlan("OPEN", 1, "Open-ended plan", null, null, START, null, MAKER);
    plan.transition(DiningPlan.Status.ACTIVE, CHECKER, "Approved", NOW, 0);
    var assignment =
        new StudentDiningAssignment(
            "dine-2",
            STUDENT,
            "r260001",
            " Student ",
            PERIOD,
            "2026-s2",
            "csc",
            group,
            hall,
            plan,
            UUID.randomUUID(),
            START,
            END.plusYears(1),
            MAKER);
    assertEquals(
        group == null || group.isBlank() ? null : "GROUP-A", assignment.getStudentGroupCode());
    assertEquals("DINE-2", assignment.getAssignmentNumber());
    assertEquals("R260001", assignment.getStudentNumber());
    assertEquals("Student", assignment.getStudentName());
    assertEquals("CSC", assignment.getProgrammeCode());
    assertNotNull(assignment.getAccommodationAllocationId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"activate", "suspend", "resume", "end", "cancel"})
  void staleVersionsCannotChangeAssignmentLifecycle(String action) {
    var assignment = assignment();
    if (action.equals("suspend") || action.equals("resume") || action.equals("end"))
      assignment.activate(CHECKER, "Approved", NOW, 0);
    if (action.equals("resume")) assignment.suspend("Suspended", 0);
    Status initial = assignment.getStatus();
    assertThrows(
        IllegalStateException.class,
        () -> {
          switch (action) {
            case "activate" -> assignment.activate(CHECKER, "Approved", NOW, 1);
            case "suspend" -> assignment.suspend("Suspended", 1);
            case "resume" -> assignment.resume("Resumed", 1);
            case "end" -> assignment.end(Status.ENDED, CHECKER, "Ended", NOW, 1);
            case "cancel" -> assignment.end(Status.CANCELLED, CHECKER, "Cancelled", NOW, 1);
            default -> fail("Unknown action");
          }
        });
    assertEquals(initial, assignment.getStatus());
  }

  @Test
  void activationRequiresIndependentOperatorAndLifecycleCannotBeSkippedOrRepeated() {
    var assignment = assignment();
    assertThrows(
        IllegalArgumentException.class, () -> assignment.activate(null, "Approved", NOW, 0));
    assertThrows(
        IllegalArgumentException.class, () -> assignment.activate(MAKER, "Self approval", NOW, 0));
    assertThrows(IllegalStateException.class, () -> assignment.suspend("Too soon", 0));
    assertThrows(IllegalStateException.class, () -> assignment.resume("Not suspended", 0));
    assertThrows(
        IllegalStateException.class,
        () -> assignment.end(Status.ENDED, CHECKER, "Too soon", NOW, 0));
    assignment.activate(CHECKER, "Approved", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> assignment.activate(CHECKER, "Repeated", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> assignment.end(Status.CANCELLED, CHECKER, "Too late", NOW, 0));
  }
}
