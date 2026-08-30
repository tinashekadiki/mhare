package zw.ac.uz.emhare.examstimetabling.timetable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.api.model.ExamTimetableApiModels.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

/**
 * @author Tinashe K
 */
class GovernedExamTimetableWorkflowTest extends ExamTimetableWorkflowFixture {
  @Test
  void largestRosterFirstAllocatesMultipleVenuesDeterministicallyAndPublishesStudentEvidence() {
    venue("B", 2);
    venue("A", 2);
    venue("LARGE", 20);
    UUID module = requirement("CSC101", 90, 30, hall);
    UUID sharedStudent = UUID.randomUUID();
    candidate("R260003", UUID.randomUUID(), module);
    candidate("R260001", sharedStudent, module);
    candidate("R260002", UUID.randomUUID(), module);
    UUID second = requirement("MAT101", 120, 0, null);
    candidate("R260001", sharedStudent, second);
    RunSummary generated = generate();
    assertEquals(3, generated.candidateCount());
    assertEquals(2, generated.moduleCount());
    assertEquals(2, generated.timetableEntryCount());
    assertEquals(0, generated.conflictCount());
    assertEquals(ACTOR, generated.generatedByUserId());
    assertEquals(NOW, generated.generatedAt());
    assertEquals("largest-roster-first-v1", generated.generationPolicy().get("algorithm"));
    assertEquals("confirmed-registration-v1", generated.generationPolicy().get("candidateSource"));
    assertEquals(
        List.of("CSC101", "MAT101"),
        generated.entries().stream().map(MasterEntrySummary::moduleCode).toList());
    MasterEntrySummary first = generated.entries().getFirst();
    assertEquals(NOW, first.startsAt());
    assertEquals(NOW.plusSeconds(7200), first.endsAt());
    assertEquals(
        List.of("A", "B"), first.venues().stream().map(VenueAllocationSummary::venueCode).toList());
    assertEquals(
        List.of(2, 1),
        first.venues().stream().map(VenueAllocationSummary::allocatedCapacity).toList());
    assertEquals(NOW.plusSeconds(7200), generated.entries().get(1).startsAt());
    var firstStudents =
        savedStudents.values().stream().filter(item -> item.getModuleId().equals(module)).toList();
    assertEquals(
        List.of("R260001", "R260002", "R260003"),
        firstStudents.stream().map(ExamStudentTimetableEntry::getStudentNumber).toList());
    assertEquals(
        List.of(1, 2, 1),
        firstStudents.stream().map(ExamStudentTimetableEntry::getSeatNumber).toList());
    assertTrue(timetable.publishedStudentTimetable(sharedStudent).isEmpty());
    assertThrows(
        IllegalStateException.class,
        () -> operations.requirePublishedVenueAllocation(first.venues().getFirst().id()));
    RunSummary published = publish(generated.id());
    assertEquals(ExamTimetableGenerationRun.Status.PUBLISHED, published.status());
    assertEquals(NOW, published.publishedAt());
    assertNotNull(published.reviewedByUserId());
    assertNotNull(published.approvedByUserId());
    assertNotNull(published.publishedByUserId());
    assertEquals(1, timetable.runs().size());
    var studentTimetable = timetable.publishedStudentTimetable(sharedStudent);
    assertEquals(2, studentTimetable.size());
    assertEquals("A", studentTimetable.getFirst().venueCode());
    assertEquals("A hall", studentTimetable.getFirst().venueName());
    assertEquals("CSC101 module", studentTimetable.getFirst().moduleName());
    assertEquals(
        ExamStudentTimetableEntry.AttendanceStatus.EXPECTED,
        studentTimetable.getFirst().attendanceStatus());
    assertEquals(3, operations.publishedVenueAllocations().size());
    var allocation = operations.requirePublishedVenueAllocation(first.venues().getFirst().id());
    assertEquals(2, operations.studentsForAllocation(allocation.getId()).size());
    assertSame(
        firstStudents.getFirst(), operations.requireStudentEntry(firstStudents.getFirst().getId()));
    verify(events, times(4)).saveAndFlush(any());
    verify(jdbc)
        .queryForObject(
            contains("pg_advisory_xact_lock"), eq(Boolean.class), eq(session.getId().toString()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no-slots",
        "no-candidates",
        "missing-requirement",
        "short-slot",
        "no-capacity",
        "wrong-venue-type",
        "availability-late",
        "availability-early",
        "no-availability",
        "published-venue-conflict"
      })
  void unschedulableEvidenceNeverPersistsAPartialTimetable(String invalid) {
    ExamVenue venue = venue("GH", 1);
    UUID module = requirement("CSC101", 120, 0, null);
    candidate("R260001", UUID.randomUUID(), module);
    switch (invalid) {
      case "no-slots" -> configuredSlots.clear();
      case "no-candidates" -> eligible.clear();
      case "missing-requirement" -> approvedRequirements.clear();
      case "short-slot" -> {
        approvedRequirements.clear();
        UUID longModule = requirement("LONG", 480, 120, null);
        eligible.clear();
        candidate("R260001", UUID.randomUUID(), longModule);
      }
      case "no-capacity" -> candidate("R260002", UUID.randomUUID(), module);
      case "wrong-venue-type" -> {
        approvedRequirements.clear();
        UUID labModule =
            requirement("LAB101", 120, 0, identified(new ExamVenueType("LAB", "Laboratory", null)));
        eligible.clear();
        candidate("R260001", UUID.randomUUID(), labModule);
      }
      case "availability-late" ->
          when(setup.availabilityFor(venue))
              .thenReturn(
                  List.of(
                      new ExamVenueAvailabilityWindow(
                          venue, NOW.plusSeconds(14401), NOW.plusSeconds(15000), null)));
      case "availability-early" ->
          when(setup.availabilityFor(venue))
              .thenReturn(
                  List.of(
                      new ExamVenueAvailabilityWindow(
                          venue, NOW.minusSeconds(10), NOW.plusSeconds(60), null)));
      case "no-availability" -> when(setup.availabilityFor(venue)).thenReturn(List.of());
      case "published-venue-conflict" ->
          when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
              .thenReturn(1);
      default -> fail("Unknown scheduling conflict");
    }
    assertThrows(IllegalStateException.class, this::generate);
    verifyNoInteractions(runs, masters, allocations, students, events);
  }

  @Test
  void venueClashesMoveDistinctStudentRostersToAdjacentNonOverlappingSlots() {
    venue("GH", 1);
    UUID first = requirement("AAA", 120, 0, null);
    UUID second = requirement("BBB", 120, 0, null);
    candidate("R260001", UUID.randomUUID(), first);
    candidate("R260002", UUID.randomUUID(), second);
    var generated = generate();
    assertEquals(
        List.of(NOW, NOW.plusSeconds(7200)),
        generated.entries().stream().map(MasterEntrySummary::startsAt).toList());
  }

  @Test
  void nullPersistentOccupancyCountDoesNotInventAConflict() {
    venue("GH", 1);
    UUID module = requirement("CSC101", 120, 0, null);
    candidate("R260001", UUID.randomUUID(), module);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(null);
    assertEquals(1, generate().timetableEntryCount());
  }

  @Test
  void exhaustedSlotsAfterAnEarlierPlacementStillSaveNoPartialRun() {
    venue("GH", 1);
    configuredSlots.removeLast();
    UUID first = requirement("AAA", 120, 0, null);
    UUID second = requirement("BBB", 120, 0, null);
    UUID student = UUID.randomUUID();
    candidate("R260001", student, first);
    candidate("R260001", student, second);
    assertThrows(IllegalStateException.class, this::generate);
    verifyNoInteractions(runs, masters, allocations, students, events);
  }

  @Test
  void unsupportedOrUnknownWorkflowActionsCannotWriteEventsAndRejectedRunCannotBeRepublished() {
    venue("GH", 1);
    UUID module = requirement("CSC101", 120, 0, null);
    candidate("R260001", UUID.randomUUID(), module);
    var generated = generate();
    clearInvocations(runs, events);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            timetable.move(
                generated.id(), "archive", new WorkflowDecision("Unsupported action", 0), ACTOR));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            timetable.move(
                UUID.randomUUID(), "review", new WorkflowDecision("Unknown run", 0), ACTOR));
    verify(runs, never()).saveAndFlush(any());
    verifyNoInteractions(events);
    var rejected =
        timetable.move(
            generated.id(),
            "reject",
            new WorkflowDecision("Venue evidence needs correction", 0),
            ACTOR);
    assertEquals(ExamTimetableGenerationRun.Status.REJECTED, rejected.status());
    assertThrows(
        IllegalStateException.class,
        () ->
            timetable.move(
                generated.id(),
                "review",
                new WorkflowDecision("Retry rejected", 0),
                UUID.randomUUID()));
  }

  @Test
  void missingPublishedAllocationAndStudentReferencesHaveExplicitErrors() {
    assertThrows(
        IllegalArgumentException.class,
        () -> operations.requirePublishedVenueAllocation(UUID.randomUUID()));
    assertThrows(
        IllegalArgumentException.class, () -> operations.requireStudentEntry(UUID.randomUUID()));
  }
}
