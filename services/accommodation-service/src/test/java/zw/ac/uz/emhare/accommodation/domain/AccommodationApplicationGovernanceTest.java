package zw.ac.uz.emhare.accommodation.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationApplication;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationApplication.Status;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationWaitlistEntry;

/**
 * @author Tinashe K
 */
class AccommodationApplicationGovernanceTest extends AccommodationGovernanceFixture {
  @ParameterizedTest
  @ValueSource(strings = {"period", "student", "programme", "submitted", "level"})
  void applicationsRequireScopedStudentProgrammeAndSubmissionEvidence(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccommodationApplication(
                "ACC-1",
                invalid.equals("period") ? null : openPeriod(),
                invalid.equals("student") ? null : STUDENT,
                "R260001",
                "Student",
                "student@example.test",
                "FEMALE",
                null,
                "ZWE",
                null,
                invalid.equals("programme") ? null : PROGRAMME,
                "CSC",
                "Computing",
                invalid.equals("level") ? 0 : 1,
                null,
                null,
                null,
                null,
                invalid.equals("submitted") ? null : NOW));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-open", "before", "after"})
  void onlySubmissionsInsideAnOpenApplicationWindowAreAccepted(String invalid) {
    var period = invalid.equals("not-open") ? draftPeriod() : openPeriod();
    assertThrows(
        IllegalStateException.class,
        () ->
            application(
                period,
                invalid.equals("before")
                    ? period.getApplicationsOpenAt().minusNanos(1)
                    : invalid.equals("after")
                        ? period.getApplicationsCloseAt().plusNanos(1)
                        : NOW));
  }

  @Test
  void windowBoundariesAreInclusiveAndApplicantEvidenceIsNormalized() {
    var period = openPeriod();
    assertEquals(Status.SUBMITTED, application(period, period.getApplicationsOpenAt()).getStatus());
    assertEquals(
        Status.SUBMITTED, application(period, period.getApplicationsCloseAt()).getStatus());
    var application =
        new AccommodationApplication(
            " acc-1 ",
            period,
            STUDENT,
            " r260001 ",
            " Student ",
            " STUDENT@EXAMPLE.TEST ",
            " female ",
            " ",
            " zwe ",
            " Harare ",
            PROGRAMME,
            " csc ",
            " Computing ",
            1,
            " Scholarship ",
            null,
            roomType,
            " Ramp access ",
            NOW);
    assertEquals("ACC-1", application.getApplicationNumber());
    assertEquals("R260001", application.getStudentNumber());
    assertEquals("student@example.test", application.getPrimaryEmail());
    assertEquals("FEMALE", application.getGenderCode());
    assertNull(application.getDisabilityCode());
    assertEquals("Harare", application.getLocationCode());
    assertEquals("Scholarship", application.getSponsorCode());
    assertEquals("Ramp access", application.getSpecialRequirements());
    assertEquals(AccommodationApplication.PaymentState.UNKNOWN, application.getPaymentState());
    assertSame(roomType, application.getPreferredRoomType());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"ELIGIBLE", "WAITLISTED", "REJECTED"})
  void authorizedEvaluationRecordsOutcomeScoreSelectionAndAuditEvidence(Status outcome) {
    var application = application();
    UUID group = UUID.randomUUID();
    application.evaluate(outcome, 25, group, " Criteria independently reviewed ", CHECKER, NOW, 0);
    assertEquals(outcome, application.getStatus());
    assertEquals(25, application.getPriorityScore());
    assertEquals(group, application.getSelectedGroupId());
    assertEquals(CHECKER, application.getEvaluatedByUserId());
    assertEquals(NOW, application.getEvaluatedAt());
    assertEquals("Criteria independently reviewed", application.getEvaluationReason());
    assertThrows(
        IllegalStateException.class,
        () -> application.evaluate(outcome, 30, group, "Repeat evaluation", CHECKER, NOW, 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"outcome", "actor", "time", "version"})
  void evaluationRejectsInvalidDecisionEvidenceBeforeChangingApplication(String invalid) {
    var application = application();
    Class<? extends RuntimeException> expected =
        invalid.equals("version") ? IllegalStateException.class : IllegalArgumentException.class;
    assertThrows(
        expected,
        () ->
            application.evaluate(
                invalid.equals("outcome") ? Status.ALLOCATED : Status.ELIGIBLE,
                10,
                null,
                "Decision evidence",
                invalid.equals("actor") ? null : CHECKER,
                invalid.equals("time") ? null : NOW,
                invalid.equals("version") ? 1 : 0));
    assertEquals(Status.SUBMITTED, application.getStatus());
    assertEquals(0, application.getPriorityScore());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"ELIGIBLE", "WAITLISTED"})
  void allocationAcceptsEligibleAndWaitlistedEvidenceAndFreezesApplicantWithdrawal(
      Status eligible) {
    var application = application();
    application.evaluate(eligible, 10, null, "Eligible evidence", CHECKER, NOW, 0);
    application.markAllocated();
    assertEquals(Status.ALLOCATED, application.getStatus());
    assertThrows(IllegalStateException.class, application::markAllocated);
    assertThrows(
        IllegalStateException.class,
        () -> application.withdraw(MAKER, "Already allocated", NOW, 0));
  }

  @Test
  void withdrawalRetainsEvidenceAndTerminalApplicationsCannotBeAllocatedOrWithdrawnAgain() {
    var application = application();
    assertThrows(IllegalStateException.class, application::markAllocated);
    assertThrows(
        IllegalStateException.class, () -> application.withdraw(MAKER, "Stale withdrawal", NOW, 1));
    application.withdraw(MAKER, " Student no longer requires residence ", NOW.plusSeconds(60), 0);
    assertEquals(Status.WITHDRAWN, application.getStatus());
    assertEquals(MAKER, application.getWithdrawnByUserId());
    assertEquals(NOW.plusSeconds(60), application.getWithdrawnAt());
    assertEquals("Student no longer requires residence", application.getWithdrawalReason());
    assertThrows(
        IllegalStateException.class,
        () -> application.withdraw(MAKER, "Repeat withdrawal", NOW, 0));
    var rejected = application();
    rejected.evaluate(Status.REJECTED, 0, null, "Not eligible", CHECKER, NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> rejected.withdraw(MAKER, "Rejected evidence", NOW, 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application", "not-waitlisted", "position", "actor", "time"})
  void waitlistRequiresARealWaitlistedApplicationPositivePositionAndEntryEvidence(String invalid) {
    var application = application();
    if (!invalid.equals("not-waitlisted"))
      application.evaluate(Status.WAITLISTED, 42, null, "Capacity full", CHECKER, NOW, 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccommodationWaitlistEntry(
                invalid.equals("application") ? null : application,
                invalid.equals("position") ? 0 : 1,
                invalid.equals("actor") ? null : CHECKER,
                invalid.equals("time") ? null : NOW));
  }

  @Test
  void waitlistRemovalRetainsPositionPriorityAndOriginalApplicationAuditTrail() {
    var application = application();
    application.evaluate(Status.WAITLISTED, 42, null, "Capacity full", CHECKER, NOW, 0);
    var entry = new AccommodationWaitlistEntry(application, 3, CHECKER, NOW);
    assertEquals(3, entry.getWaitlistPosition());
    assertEquals(42, entry.getPriorityScore());
    assertSame(application, entry.getApplication());
    assertSame(application.getApplicationPeriod(), entry.getApplicationPeriod());
    assertEquals(CHECKER, entry.getEnteredByUserId());
    assertEquals(NOW, entry.getEnteredAt());
    assertThrows(
        IllegalArgumentException.class,
        () -> entry.remove(AccommodationWaitlistEntry.Status.ACTIVE, CHECKER, "Not removed", NOW));
    entry.remove(
        AccommodationWaitlistEntry.Status.ALLOCATED,
        WARDEN,
        " Allocated released capacity ",
        NOW.plusSeconds(60));
    assertEquals(AccommodationWaitlistEntry.Status.ALLOCATED, entry.getStatus());
    assertEquals(WARDEN, entry.getRemovedByUserId());
    assertEquals(NOW.plusSeconds(60), entry.getRemovedAt());
    assertEquals("Allocated released capacity", entry.getRemovalReason());
    assertThrows(
        IllegalStateException.class,
        () -> entry.remove(AccommodationWaitlistEntry.Status.REMOVED, WARDEN, "Repeated", NOW));
  }
}
