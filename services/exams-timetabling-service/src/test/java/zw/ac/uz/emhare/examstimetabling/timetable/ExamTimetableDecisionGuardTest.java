package zw.ac.uz.emhare.examstimetabling.timetable;

import static org.junit.jupiter.api.Assertions.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSession;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.ExamTimetableGenerationRun;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.ExamTimetableGenerationRun.Status;

/**
 * @author Tinashe K
 */
class ExamTimetableDecisionGuardTest {
  private final UUID reviewer = UUID.randomUUID();
  private final UUID approver = UUID.randomUUID();
  private final UUID publisher = UUID.randomUUID();

  @ParameterizedTest
  @ValueSource(strings = {"review", "approve", "publish", "reject"})
  void staleWorkflowDecisionsCannotChangeStatus(String action) {
    var run = run(0);
    if (action.equals("approve") || action.equals("publish"))
      run.review(reviewer, "Independent review", NOW, 0);
    if (action.equals("publish")) run.approve(approver, "Board approval", NOW, 0);
    Status initial = run.getStatus();
    assertThrows(
        IllegalStateException.class,
        () -> {
          switch (action) {
            case "review" -> run.review(reviewer, "Review", NOW, 1);
            case "approve" -> run.approve(approver, "Approve", NOW, 1);
            case "publish" -> run.publish(publisher, "Publish", NOW, 1);
            case "reject" -> run.reject(publisher, "Reject", NOW, 1);
            default -> fail("Unknown workflow action");
          }
        });
    assertEquals(initial, run.getStatus());
  }

  @Test
  void conflictingTimetableCannotBeApprovedEvenAfterIndependentReview() {
    var run = run(1);
    run.review(reviewer, "Conflicts identified", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> run.approve(approver, "Attempt approval", NOW, 0));
    assertEquals(Status.REVIEWED, run.getStatus());
    assertNull(run.getApprovedByUserId());
  }

  @Test
  void generatorCannotApproveOrPublishAndReviewerCannotPublish() {
    var run = run(0);
    run.review(reviewer, "Independent review", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> run.approve(ACTOR, "Generator approval", NOW, 0));
    run.approve(approver, "Board approval", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> run.publish(ACTOR, "Generator publication", NOW, 0));
    assertThrows(
        IllegalStateException.class, () -> run.publish(reviewer, "Reviewer publication", NOW, 0));
    assertEquals(Status.APPROVED, run.getStatus());
    assertNull(run.getPublishedByUserId());
  }

  @Test
  void rejectionIsTerminalAndPublishedEvidenceCannotBeRejected() {
    var rejected = run(0);
    assertEquals(Status.GENERATED, rejected.reject(reviewer, "Unsuitable allocation", NOW, 0));
    assertThrows(
        IllegalStateException.class, () -> rejected.reject(reviewer, "Repeat rejection", NOW, 0));
    var published = run(0);
    published.review(reviewer, "Review", NOW, 0);
    published.approve(approver, "Approve", NOW, 0);
    published.publish(publisher, "Publish", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> published.reject(reviewer, "Undo publication", NOW, 0));
    assertEquals(Status.PUBLISHED, published.getStatus());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void decisionsRequireMeaningfulAuditEvidence(String reason) {
    var run = run(0);
    assertThrows(IllegalArgumentException.class, () -> run.review(reviewer, reason, NOW, 0));
    assertEquals(Status.GENERATED, run.getStatus());
  }

  private ExamTimetableGenerationRun run(int conflicts) {
    ExamSession session =
        new ExamSession(
            PERIOD,
            "2026-S2",
            "FINAL",
            "Final",
            ExamSession.AssessmentType.FINAL_EXAM,
            START,
            START);
    session.approve(ACTOR, "Approved session", NOW, 0);
    return new ExamTimetableGenerationRun(
        session,
        "EXM-1",
        1,
        1,
        1,
        conflicts,
        Map.of("algorithm", "largest-roster-first-v1"),
        ACTOR,
        NOW);
  }
}
