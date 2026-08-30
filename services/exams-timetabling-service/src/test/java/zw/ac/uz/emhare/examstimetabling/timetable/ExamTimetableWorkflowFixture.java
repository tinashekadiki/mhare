package zw.ac.uz.emhare.examstimetabling.timetable;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.ac.uz.emhare.examstimetabling.ExamTestData.RegistrationEvidence;
import zw.ac.uz.emhare.examstimetabling.roster.ExamRosterQueryService;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.ExamTimetableSetupQueryService;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.api.model.ExamTimetableApiModels.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.infrastructure.persistence.*;

/**
 * Real schedule aggregates with only repository and upstream query boundaries mocked. @author
 * Tinashe K
 */
public abstract class ExamTimetableWorkflowFixture {
  protected final ExamTimetableGenerationRunRepository runs =
      mock(ExamTimetableGenerationRunRepository.class);
  protected final ExamMasterTimetableEntryRepository masters =
      mock(ExamMasterTimetableEntryRepository.class);
  protected final ExamTimetableVenueAllocationRepository allocations =
      mock(ExamTimetableVenueAllocationRepository.class);
  protected final ExamStudentTimetableEntryRepository students =
      mock(ExamStudentTimetableEntryRepository.class);
  protected final ExamTimetableRunEventRepository events =
      mock(ExamTimetableRunEventRepository.class);
  protected final ExamTimetableSetupQueryService setup = mock(ExamTimetableSetupQueryService.class);
  protected final ExamRosterQueryService roster = mock(ExamRosterQueryService.class);
  protected final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  protected final Map<UUID, ExamTimetableGenerationRun> savedRuns = new LinkedHashMap<>();
  protected final Map<UUID, ExamMasterTimetableEntry> savedMasters = new LinkedHashMap<>();
  protected final Map<UUID, ExamTimetableVenueAllocation> savedAllocations = new LinkedHashMap<>();
  protected final Map<UUID, ExamStudentTimetableEntry> savedStudents = new LinkedHashMap<>();
  protected final List<ExamCandidateModule> eligible = new ArrayList<>();
  protected final List<ExamVenue> availableVenues = new ArrayList<>();
  protected final List<ExamSessionSlot> configuredSlots = new ArrayList<>();
  protected final Map<UUID, ModuleExamRequirement> approvedRequirements = new LinkedHashMap<>();
  protected final ExamVenueType hall = identified(new ExamVenueType("HALL", "Hall", null));
  protected final ExamSession session =
      identified(
          new ExamSession(
              PERIOD,
              "2026-S2",
              "FINAL",
              "Final Exams",
              ExamSession.AssessmentType.FINAL_EXAM,
              START,
              START.plusDays(5)));
  protected final GovernedExamTimetableService timetable =
      new GovernedExamTimetableService(
          runs,
          masters,
          allocations,
          students,
          events,
          setup,
          roster,
          jdbc,
          Clock.fixed(NOW, ZoneOffset.UTC));
  protected final ExamTimetableOperationsQueryService operations =
      new ExamTimetableOperationsQueryService(allocations, students);

  @BeforeEach
  protected void configureWorkflowRepositories() {
    configuredSlots.add(identified(new ExamSessionSlot(session, "AM", NOW, NOW.plusSeconds(7200))));
    configuredSlots.add(
        identified(
            new ExamSessionSlot(session, "PM", NOW.plusSeconds(7200), NOW.plusSeconds(14400))));
    session.approve(ACTOR, "Approved examination session", NOW, 0);
    when(setup.requireApprovedSession(session.getId())).thenReturn(session);
    when(setup.slots(session.getId())).thenReturn(configuredSlots);
    when(setup.activeVenues()).thenReturn(availableVenues);
    when(setup.approvedRequirements(PERIOD)).thenReturn(approvedRequirements);
    when(roster.eligibleCandidates(PERIOD)).thenReturn(eligible);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(0);
    when(runs.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamTimetableGenerationRun item = identified(invocation.getArgument(0));
              savedRuns.put(item.getId(), item);
              return item;
            });
    when(masters.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamMasterTimetableEntry item = identified(invocation.getArgument(0));
              savedMasters.put(item.getId(), item);
              return item;
            });
    when(allocations.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamTimetableVenueAllocation item = identified(invocation.getArgument(0));
              savedAllocations.put(item.getId(), item);
              return item;
            });
    when(students.saveAllAndFlush(any()))
        .thenAnswer(
            invocation -> {
              List<ExamStudentTimetableEntry> rows = invocation.getArgument(0);
              rows.forEach(item -> savedStudents.put(identified(item).getId(), item));
              return rows;
            });
    when(runs.findByIdAndDeletedAtIsNull(any()))
        .thenAnswer(invocation -> Optional.ofNullable(savedRuns.get(invocation.getArgument(0))));
    when(allocations.findById(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedAllocations.get(invocation.getArgument(0))));
    when(students.findById(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedStudents.get(invocation.getArgument(0))));
    when(runs.findAllByDeletedAtIsNullOrderByGeneratedAtDesc())
        .thenAnswer(invocation -> List.copyOf(savedRuns.values()));
    when(masters.findAllByGenerationRunIdAndDeletedAtIsNullOrderByScheduledStartsAtAscModuleCodeAsc(
            any()))
        .thenAnswer(
            invocation ->
                savedMasters.values().stream()
                    .filter(
                        item -> item.getGenerationRun().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(allocations.findAllByMasterTimetableEntryIdAndDeletedAtIsNullOrderByVenueCodeAsc(any()))
        .thenAnswer(
            invocation ->
                savedAllocations.values().stream()
                    .filter(
                        item ->
                            item.getMasterTimetableEntry()
                                .getId()
                                .equals(invocation.getArgument(0)))
                    .toList());
    when(students
            .findAllByStudentIdAndGenerationRunStatusAndDeletedAtIsNullOrderByScheduledStartsAtAsc(
                any(), any()))
        .thenAnswer(
            invocation ->
                savedStudents.values().stream()
                    .filter(
                        item ->
                            item.getStudentId().equals(invocation.getArgument(0))
                                && item.getGenerationRun().getStatus() == invocation.getArgument(1))
                    .toList());
    when(students.findAllByVenueAllocationIdAndDeletedAtIsNullOrderBySeatNumberAsc(any()))
        .thenAnswer(
            invocation ->
                savedStudents.values().stream()
                    .filter(
                        item -> item.getVenueAllocation().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(allocations
            .findAllByMasterTimetableEntryGenerationRunStatusAndDeletedAtIsNullOrderByMasterTimetableEntryScheduledStartsAtAscVenueCodeAsc(
                any()))
        .thenAnswer(
            invocation ->
                savedAllocations.values().stream()
                    .filter(
                        item ->
                            item.getMasterTimetableEntry().getGenerationRun().getStatus()
                                == invocation.getArgument(0))
                    .toList());
  }

  protected ExamVenue venue(String code, int capacity) {
    return venue(code, capacity, hall);
  }

  protected ExamVenue venue(String code, int capacity, ExamVenueType type) {
    ExamVenue venue =
        identified(
            new ExamVenue(type, code, code + " hall", "Main Campus", null, null, capacity, null));
    availableVenues.add(venue);
    when(setup.availabilityFor(venue))
        .thenReturn(
            List.of(new ExamVenueAvailabilityWindow(venue, NOW, NOW.plusSeconds(14400), null)));
    return venue;
  }

  protected UUID requirement(String code, int duration, int reading, ExamVenueType type) {
    UUID module = UUID.randomUUID();
    ModuleExamRequirement requirement =
        identified(
            new ModuleExamRequirement(
                PERIOD, module, code, code + " module", 1, duration, reading, type, null));
    requirement.approve(ACTOR, "Approved specification", NOW, 0);
    approvedRequirements.put(module, requirement);
    return module;
  }

  protected ExamCandidateModule candidate(String studentNumber, UUID studentId, UUID module) {
    RegistrationEvidence evidence = new RegistrationEvidence();
    evidence.studentNumber = studentNumber;
    evidence.studentId = studentId;
    evidence.modules = List.of(module(module, approvedRequirements.get(module).getModuleCode()));
    ExamCandidateModule candidate =
        identified(
            new ExamCandidateModule(
                identified(new ExamRegistrationImport(evidence.event(), NOW)),
                evidence.modules.getFirst()));
    eligible.add(candidate);
    return candidate;
  }

  protected RunSummary generate() {
    return timetable.generate(new GenerateTimetable(session.getId()), ACTOR);
  }

  protected RunSummary publish(UUID runId) {
    timetable.move(
        runId,
        "review",
        new WorkflowDecision("Independently checked all clashes", 0),
        UUID.randomUUID());
    timetable.move(
        runId, "approve", new WorkflowDecision("Examination board approved", 0), UUID.randomUUID());
    return timetable.move(
        runId,
        "publish",
        new WorkflowDecision("Release approved timetable to students", 0),
        UUID.randomUUID());
  }

  protected ExamTimetableVenueAllocation publishedRoster(int size) {
    venue("GH", size);
    UUID module = requirement("CSC101", 120, 0, null);
    for (int index = 1; index <= size; index++)
      candidate("R26000" + index, UUID.randomUUID(), module);
    RunSummary run = generate();
    publish(run.id());
    return savedAllocations.values().iterator().next();
  }
}
