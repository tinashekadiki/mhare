package zw.ac.uz.emhare.examstimetabling.invigilation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import zw.ac.uz.emhare.examstimetabling.invigilation.api.model.ExamInvigilationApiModels.*;
import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.invigilation.infrastructure.persistence.*;
import zw.ac.uz.emhare.examstimetabling.timetable.ExamTimetableWorkflowFixture;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

/**
 * @author Tinashe K
 */
class GovernedExamInvigilationWorkflowTest extends ExamTimetableWorkflowFixture {
  private final ExamAttendanceSessionRepository sessions =
      mock(ExamAttendanceSessionRepository.class);
  private final ExamAttendanceRecordRepository records = mock(ExamAttendanceRecordRepository.class);
  private final ExamIncidentReportRepository incidents = mock(ExamIncidentReportRepository.class);
  private final Map<UUID, ExamAttendanceSession> savedSessions = new LinkedHashMap<>();
  private final Map<UUID, ExamAttendanceRecord> savedRecords = new LinkedHashMap<>();
  private final Map<UUID, ExamIncidentReport> savedIncidents = new LinkedHashMap<>();
  private final GovernedExamInvigilationService invigilation =
      new GovernedExamInvigilationService(
          sessions, records, incidents, operations, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void persistAttendanceEvidence() {
    when(sessions.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamAttendanceSession item = identified(invocation.getArgument(0));
              savedSessions.put(item.getId(), item);
              return item;
            });
    when(records.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamAttendanceRecord item = identified(invocation.getArgument(0));
              savedRecords.put(item.getId(), item);
              return item;
            });
    when(records.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ExamAttendanceRecord> rows = invocation.getArgument(0);
              rows.forEach(item -> savedRecords.put(identified(item).getId(), item));
              return rows;
            });
    when(incidents.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamIncidentReport item = identified(invocation.getArgument(0));
              savedIncidents.put(item.getId(), item);
              return item;
            });
    when(sessions.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedSessions.get(invocation.getArgument(0))));
    when(records.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(invocation -> Optional.ofNullable(savedRecords.get(invocation.getArgument(0))));
    when(incidents.findLockedByIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedIncidents.get(invocation.getArgument(0))));
    when(sessions.findByVenueAllocationIdAndDeletedAtIsNull(any()))
        .thenAnswer(
            invocation ->
                savedSessions.values().stream()
                    .filter(
                        item -> item.getVenueAllocation().getId().equals(invocation.getArgument(0)))
                    .findFirst());
    when(records
            .findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByStudentTimetableEntrySeatNumberAsc(
                any()))
        .thenAnswer(
            invocation ->
                savedRecords.values().stream()
                    .filter(
                        item ->
                            item.getAttendanceSession().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(incidents.findAllByAttendanceSessionIdAndDeletedAtIsNullOrderByOccurredAtDesc(any()))
        .thenAnswer(
            invocation ->
                savedIncidents.values().stream()
                    .filter(
                        item ->
                            item.getAttendanceSession().getId().equals(invocation.getArgument(0)))
                    .toList());
  }

  @Test
  void publishedRosterIsReconciledIntoAnAuditableAttendanceSessionWithoutChangingTimetable() {
    ExamTimetableVenueAllocation allocation = publishedRoster(3);
    var available = invigilation.workspace().venueOperations().getFirst();
    assertEquals("CSC101", available.moduleCode());
    assertEquals("Main Campus", available.campusName());
    assertEquals(3, available.allocatedCandidateCount());
    assertNull(available.attendanceSession());
    var opened =
        invigilation.open(
            allocation.getId(), new OpenAttendanceSession(" Open against checked roster "), ACTOR);
    assertEquals(ExamAttendanceSession.Status.OPEN, opened.status());
    assertEquals(3, opened.outstandingCandidateCount());
    assertEquals(ACTOR, opened.openedByUserId());
    assertEquals(NOW, opened.openedAt());
    assertEquals("Open against checked roster", opened.openingReason());
    var attendance = opened.attendanceRecords();
    invigilation.recordAttendance(
        attendance.get(0).id(),
        new RecordAttendance(ExamAttendanceRecord.Status.PRESENT, " ", 0),
        ACTOR);
    invigilation.recordAttendance(
        attendance.get(1).id(),
        new RecordAttendance(ExamAttendanceRecord.Status.ABSENT, " Candidate did not attend ", 0),
        ACTOR);
    var reconciled =
        invigilation.recordAttendance(
            attendance.get(2).id(),
            new RecordAttendance(
                ExamAttendanceRecord.Status.EXCUSED, "Medical certificate accepted", 0),
            ACTOR);
    assertEquals(1, reconciled.presentCandidateCount());
    assertEquals(1, reconciled.absentCandidateCount());
    assertEquals(1, reconciled.excusedCandidateCount());
    assertEquals(0, reconciled.outstandingCandidateCount());
    assertNull(reconciled.attendanceRecords().getFirst().evidenceNotes());
    assertEquals("Candidate did not attend", reconciled.attendanceRecords().get(1).evidenceNotes());
    assertEquals(ACTOR, reconciled.attendanceRecords().getFirst().recordedByUserId());
    assertEquals(NOW, reconciled.attendanceRecords().getFirst().recordedAt());
    var closed =
        invigilation.close(
            opened.id(), new CloseAttendanceSession(" All published seats reconciled ", 0), ACTOR);
    assertEquals(ExamAttendanceSession.Status.CLOSED, closed.status());
    assertEquals(ACTOR, closed.closedByUserId());
    assertEquals(NOW, closed.closedAt());
    assertEquals("All published seats reconciled", closed.closureReason());
    assertEquals(closed, invigilation.workspace().venueOperations().getFirst().attendanceSession());
    assertTrue(
        savedStudents.values().stream()
            .allMatch(
                item ->
                    item.getAttendanceStatus()
                        == ExamStudentTimetableEntry.AttendanceStatus.EXPECTED));
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.recordAttendance(
                attendance.getFirst().id(),
                new RecordAttendance(ExamAttendanceRecord.Status.ABSENT, "Late amendment", 0),
                ACTOR));
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.close(
                opened.id(), new CloseAttendanceSession("Repeated close", 0), ACTOR));
  }

  @ParameterizedTest
  @ValueSource(strings = {"duplicate", "mismatched-roster", "concurrent-open"})
  void openingCannotDuplicateOrUnderpopulateThePublishedRoster(String invalid) {
    ExamTimetableVenueAllocation allocation = publishedRoster(1);
    if ("duplicate".equals(invalid))
      invigilation.open(allocation.getId(), new OpenAttendanceSession("First open"), ACTOR);
    if ("mismatched-roster".equals(invalid)) savedStudents.clear();
    if ("concurrent-open".equals(invalid))
      doThrow(new DataIntegrityViolationException("unique allocation"))
          .when(sessions)
          .saveAndFlush(any());
    clearInvocations(records);
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR));
    verify(records, never()).saveAllAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"attendance", "closure", "report-session", "incident"})
  void missingAttendanceReferencesCannotProduceEvidence(String missing) {
    UUID unknown = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          switch (missing) {
            case "attendance" ->
                invigilation.recordAttendance(
                    unknown,
                    new RecordAttendance(ExamAttendanceRecord.Status.PRESENT, null, 0),
                    ACTOR);
            case "closure" ->
                invigilation.close(unknown, new CloseAttendanceSession("Closure", 0), ACTOR);
            case "report-session" -> invigilation.reportIncident(unknown, report(null), ACTOR);
            case "incident" ->
                invigilation.moveIncident(
                    unknown, "review", new IncidentWorkflowDecision("Review", 0), ACTOR);
            default -> fail("Unknown reference");
          }
        });
    verify(sessions, never()).saveAndFlush(any());
    verify(records, never()).saveAndFlush(any());
    verify(incidents, never()).saveAndFlush(any());
  }

  @Test
  void closureRequiresCompleteOutcomesAndCurrentVersion() {
    var allocation = publishedRoster(1);
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.close(
                opened.id(), new CloseAttendanceSession("Outstanding seats", 0), ACTOR));
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.close(opened.id(), new CloseAttendanceSession("Stale change", 1), ACTOR));
    assertEquals(ExamAttendanceSession.Status.OPEN, savedSessions.get(opened.id()).getStatus());
    assertThrows(
        IllegalStateException.class,
        () ->
            invigilation.recordAttendance(
                opened.attendanceRecords().getFirst().id(),
                new RecordAttendance(ExamAttendanceRecord.Status.PRESENT, null, 1),
                ACTOR));
    assertEquals(
        ExamAttendanceRecord.Status.EXPECTED,
        savedRecords.values().iterator().next().getAttendanceStatus());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null-status",
        "expected",
        "absent-null",
        "absent-blank",
        "excused-null",
        "excused-blank"
      })
  void unrecordedOutcomesAndAbsenceWithoutEvidenceAreRejected(String invalid) {
    var allocation = publishedRoster(1);
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    ExamAttendanceRecord.Status status =
        invalid.startsWith("absent")
            ? ExamAttendanceRecord.Status.ABSENT
            : invalid.startsWith("excused")
                ? ExamAttendanceRecord.Status.EXCUSED
                : "expected".equals(invalid) ? ExamAttendanceRecord.Status.EXPECTED : null;
    assertThrows(
        IllegalArgumentException.class,
        () ->
            invigilation.recordAttendance(
                opened.attendanceRecords().getFirst().id(),
                new RecordAttendance(status, invalid.endsWith("blank") ? " " : null, 0),
                ACTOR));
    assertEquals(
        ExamAttendanceRecord.Status.EXPECTED,
        savedRecords.values().iterator().next().getAttendanceStatus());
  }

  @Test
  void candidateAndWholeVenueIncidentsKeepOriginalEvidenceThroughIndependentReviewAndResolution() {
    var allocation = publishedRoster(1);
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    UUID studentEntry = opened.attendanceRecords().getFirst().studentTimetableEntryId();
    invigilation.reportIncident(opened.id(), report(studentEntry), ACTOR);
    var reported = invigilation.reportIncident(opened.id(), report(null), ACTOR);
    assertEquals(2, reported.incidents().size());
    var candidateIncident = reported.incidents().getFirst();
    var venueIncident = reported.incidents().getLast();
    assertEquals(studentEntry, candidateIncident.studentTimetableEntryId());
    assertEquals("R260001", candidateIncident.studentNumber());
    assertNull(venueIncident.studentTimetableEntryId());
    assertNull(venueIncident.studentNumber());
    assertEquals(NOW, candidateIncident.reportedAt());
    assertEquals(ACTOR, candidateIncident.reportedByUserId());
    assertEquals(ExamIncidentReport.Type.DISRUPTION, candidateIncident.incidentType());
    assertEquals(ExamIncidentReport.Severity.HIGH, candidateIncident.severity());
    assertTrue(candidateIncident.incidentNumber().startsWith("INC-"));
    UUID reviewer = UUID.randomUUID();
    UUID resolver = UUID.randomUUID();
    invigilation.moveIncident(
        candidateIncident.id(),
        "review",
        new IncidentWorkflowDecision(" Independent evidence review ", 0),
        reviewer);
    var resolved =
        invigilation
            .moveIncident(
                candidateIncident.id(),
                "resolve",
                new IncidentWorkflowDecision(" Additional time authorized ", 0),
                resolver)
            .incidents()
            .getFirst();
    assertEquals(ExamIncidentReport.Status.RESOLVED, resolved.status());
    assertEquals(reviewer, resolved.reviewedByUserId());
    assertEquals(resolver, resolved.resolvedByUserId());
    assertEquals(NOW, resolved.reviewedAt());
    assertEquals(NOW, resolved.resolvedAt());
    assertEquals("Independent evidence review", resolved.reviewReason());
    assertEquals("Additional time authorized", resolved.resolution());
    assertEquals(candidateIncident.description(), resolved.description());
    assertEquals(candidateIncident.occurredAt(), resolved.occurredAt());
  }

  @Test
  void incidentCandidateMustBelongToTheAttendanceVenue() {
    var allocation = publishedRoster(1);
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    var original = savedStudents.values().iterator().next();
    var otherAllocation =
        identified(
            new ExamTimetableVenueAllocation(
                original.getMasterTimetableEntry(), allocation.getVenue(), 1));
    var other =
        identified(
            new ExamStudentTimetableEntry(
                original.getGenerationRun(),
                original.getMasterTimetableEntry(),
                otherAllocation,
                eligible.getFirst(),
                1));
    savedStudents.put(other.getId(), other);
    assertThrows(
        IllegalArgumentException.class,
        () -> invigilation.reportIncident(opened.id(), report(other.getId()), ACTOR));
    verify(incidents, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "self-review",
        "resolve-before-review",
        "stale-review",
        "repeat-review",
        "reporter-resolution",
        "reviewer-resolution",
        "stale-resolution",
        "unsupported-action"
      })
  void incidentWorkflowEnforcesSegregationOrderAndOptimisticVersion(String invalid) {
    var allocation = publishedRoster(1);
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    var incident =
        invigilation.reportIncident(opened.id(), report(null), ACTOR).incidents().getFirst();
    UUID reviewer = UUID.randomUUID();
    boolean reviewed =
        Set.of("repeat-review", "reporter-resolution", "reviewer-resolution", "stale-resolution")
            .contains(invalid);
    if (reviewed)
      invigilation.moveIncident(
          incident.id(), "review", new IncidentWorkflowDecision("Independent review", 0), reviewer);
    String action =
        invalid.contains("resolution") || invalid.equals("resolve-before-review")
            ? "resolve"
            : invalid.equals("unsupported-action") ? "archive" : "review";
    UUID actor =
        invalid.equals("self-review") || invalid.equals("reporter-resolution")
            ? ACTOR
            : invalid.equals("reviewer-resolution") ? reviewer : UUID.randomUUID();
    long version = invalid.startsWith("stale") ? 1 : 0;
    Class<? extends RuntimeException> expected =
        invalid.equals("unsupported-action")
            ? IllegalArgumentException.class
            : IllegalStateException.class;
    clearInvocations(incidents);
    assertThrows(
        expected,
        () ->
            invigilation.moveIncident(
                incident.id(),
                action,
                new IncidentWorkflowDecision("Invalid transition", version),
                actor));
    assertEquals(
        reviewed ? ExamIncidentReport.Status.REVIEWED : ExamIncidentReport.Status.REPORTED,
        savedIncidents.get(incident.id()).getStatus());
    verify(incidents, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void requiredAttendanceAndIncidentEvidenceCannotBeBlank(String reason) {
    var allocation = publishedRoster(1);
    assertThrows(
        IllegalArgumentException.class,
        () -> invigilation.open(allocation.getId(), new OpenAttendanceSession(reason), ACTOR));
    assertTrue(savedSessions.isEmpty());
    var opened =
        invigilation.open(allocation.getId(), new OpenAttendanceSession("Open roster"), ACTOR);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            invigilation.reportIncident(
                opened.id(),
                new ReportIncident(
                    null,
                    ExamIncidentReport.Type.OTHER,
                    ExamIncidentReport.Severity.LOW,
                    reason,
                    NOW),
                ACTOR));
    assertTrue(savedIncidents.isEmpty());
  }

  @Test
  void emptyAttendanceSessionsCannotBeCreatedEvenBeforeRepositoryPersistence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExamAttendanceSession(null, 0, ACTOR, NOW, "No candidates"));
  }

  private ReportIncident report(UUID studentEntry) {
    return new ReportIncident(
        studentEntry,
        ExamIncidentReport.Type.DISRUPTION,
        ExamIncidentReport.Severity.HIGH,
        " Power interruption affected the examination room ",
        NOW.minusSeconds(30));
  }
}
