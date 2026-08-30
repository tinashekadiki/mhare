package zw.ac.uz.emhare.examstimetabling.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static zw.ac.uz.emhare.examstimetabling.ExamTestData.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zw.ac.uz.emhare.examstimetabling.setup.api.model.ExamSetupApiModels.*;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class ExamSetupGovernanceServiceTest {
  private final ExamVenueTypeRepository types = mock(ExamVenueTypeRepository.class);
  private final ExamVenueRepository venues = mock(ExamVenueRepository.class);
  private final ExamVenueAvailabilityRepository windows =
      mock(ExamVenueAvailabilityRepository.class);
  private final ExamSessionRepository sessions = mock(ExamSessionRepository.class);
  private final ExamSessionSlotRepository slots = mock(ExamSessionSlotRepository.class);
  private final ModuleExamRequirementRepository requirements =
      mock(ModuleExamRequirementRepository.class);
  private final Map<UUID, ExamVenueType> savedTypes = new LinkedHashMap<>();
  private final Map<UUID, ExamVenue> savedVenues = new LinkedHashMap<>();
  private final Map<UUID, ExamSession> savedSessions = new LinkedHashMap<>();
  private final Map<UUID, ModuleExamRequirement> savedRequirements = new LinkedHashMap<>();
  private final List<ExamVenueAvailabilityWindow> savedWindows = new ArrayList<>();
  private final List<ExamSessionSlot> savedSlots = new ArrayList<>();
  private final ExamSetupService service =
      new ExamSetupService(
          types, venues, windows, sessions, slots, requirements, Clock.fixed(NOW, ZoneOffset.UTC));
  private final ExamTimetableSetupQueryService query =
      new ExamTimetableSetupQueryService(sessions, slots, requirements, venues, windows);

  @BeforeEach
  void persistRealSetupAggregates() {
    when(types.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamVenueType item = identified(invocation.getArgument(0));
              savedTypes.put(item.getId(), item);
              return item;
            });
    when(venues.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamVenue item = identified(invocation.getArgument(0));
              savedVenues.put(item.getId(), item);
              return item;
            });
    when(sessions.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamSession item = identified(invocation.getArgument(0));
              savedSessions.put(item.getId(), item);
              return item;
            });
    when(requirements.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ModuleExamRequirement item = identified(invocation.getArgument(0));
              savedRequirements.put(item.getId(), item);
              return item;
            });
    when(windows.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamVenueAvailabilityWindow item = identified(invocation.getArgument(0));
              savedWindows.add(item);
              return item;
            });
    when(slots.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              ExamSessionSlot item = identified(invocation.getArgument(0));
              savedSlots.add(item);
              return item;
            });
    when(types.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(savedTypes.get(invocation.getArgument(0))));
    when(venues.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(savedVenues.get(invocation.getArgument(0))));
    when(sessions.findById(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedSessions.get(invocation.getArgument(0))));
    when(requirements.findById(any()))
        .thenAnswer(
            invocation -> Optional.ofNullable(savedRequirements.get(invocation.getArgument(0))));
    when(types.findAllByDeletedAtIsNullOrderByCodeAsc())
        .thenAnswer(invocation -> List.copyOf(savedTypes.values()));
    when(venues.findAllByActiveTrueAndDeletedAtIsNullOrderByCodeAsc())
        .thenAnswer(invocation -> List.copyOf(savedVenues.values()));
    when(sessions.findAllByDeletedAtIsNullOrderByStartsOnDescCodeAsc())
        .thenAnswer(invocation -> List.copyOf(savedSessions.values()));
    when(requirements.findAllByDeletedAtIsNullOrderByModuleCodeAscRequirementVersionDesc())
        .thenAnswer(invocation -> List.copyOf(savedRequirements.values()));
    when(windows.findAllByVenueIdAndDeletedAtIsNullOrderByAvailableFromAsc(any()))
        .thenAnswer(
            invocation ->
                savedWindows.stream()
                    .filter(item -> item.getVenue().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(slots.findAllByExamSessionIdAndDeletedAtIsNullOrderByStartsAtAsc(any()))
        .thenAnswer(
            invocation ->
                savedSlots.stream()
                    .filter(item -> item.getExamSession().getId().equals(invocation.getArgument(0)))
                    .toList());
    when(requirements.findByAcademicPeriodIdAndModuleIdAndStatusAndDeletedAtIsNull(
            any(), any(), any()))
        .thenAnswer(
            invocation ->
                savedRequirements.values().stream()
                    .filter(
                        item ->
                            item.getAcademicPeriodId().equals(invocation.getArgument(0))
                                && item.getModuleId().equals(invocation.getArgument(1))
                                && item.getStatus() == invocation.getArgument(2))
                    .findFirst());
    when(requirements.findAllByAcademicPeriodIdAndStatusAndDeletedAtIsNull(any(), any()))
        .thenAnswer(
            invocation ->
                savedRequirements.values().stream()
                    .filter(
                        item ->
                            item.getAcademicPeriodId().equals(invocation.getArgument(0))
                                && item.getStatus() == invocation.getArgument(1))
                    .toList());
  }

  @Test
  void approvedSetupRegisterExposesVenueAvailabilitySlotsAndVersionedModuleRequirements() {
    var type =
        service.createVenueType(new CreateVenueType(" hall ", " Main Hall ", " Flexible seating "));
    var venue =
        service.createVenue(
            new CreateVenue(
                type.id(),
                " gh ",
                " Great Hall ",
                " Main Campus ",
                " Central ",
                " GH ",
                100,
                " Ramp access "));
    venue =
        service.addAvailability(
            venue.id(),
            new AddAvailability(NOW, NOW.plusSeconds(7200), " Reserved for examinations "));
    assertEquals("GH", venue.code());
    assertEquals("HALL", venue.venueTypeCode());
    assertEquals("Great Hall", venue.name());
    assertEquals("Main Campus", venue.campusName());
    assertEquals("Central", venue.buildingName());
    assertEquals("GH", venue.roomName());
    assertEquals("Ramp access", venue.accessibilityNotes());
    assertTrue(venue.active());
    assertEquals("Reserved for examinations", venue.availability().getFirst().notes());
    assertEquals(NOW.plusSeconds(7200), venue.availability().getFirst().availableUntil());
    var session =
        service.createSession(
            new CreateSession(
                PERIOD,
                "2026-S2",
                " final ",
                " Final Exams ",
                ExamSession.AssessmentType.FINAL_EXAM,
                START,
                START.plusDays(10)));
    assertThrows(IllegalStateException.class, () -> query.requireApprovedSession(session.id()));
    var slotted = service.addSlot(session.id(), new CreateSlot(" am ", NOW, NOW.plusSeconds(7200)));
    assertEquals("AM", slotted.slots().getFirst().code());
    var approved =
        service.approveSession(
            session.id(), new WorkflowDecision(" Senate approved examination window ", 0), ACTOR);
    assertEquals(ExamSession.Status.APPROVED, approved.status());
    assertEquals(ACTOR, approved.approvedByUserId());
    assertEquals(NOW, approved.approvedAt());
    assertEquals("Senate approved examination window", approved.approvalReason());
    assertSame(savedSessions.get(session.id()), query.requireApprovedSession(session.id()));
    UUID module = UUID.randomUUID();
    var first = service.createRequirement(requirement(module, type.id()));
    service.approveRequirement(
        first.id(), new WorkflowDecision("Exam board confirmed requirements", 0), ACTOR);
    var next = service.createRequirement(requirement(module, type.id()));
    assertEquals(2, next.requirementVersion());
    var approvedNext =
        service.approveRequirement(
            next.id(), new WorkflowDecision("Revised approved specification", 0), ACTOR);
    assertEquals(
        ModuleExamRequirement.Status.SUPERSEDED, savedRequirements.get(first.id()).getStatus());
    assertEquals(ModuleExamRequirement.Status.APPROVED, approvedNext.status());
    assertEquals(type.id(), approvedNext.requiredVenueTypeId());
    assertEquals("HALL", approvedNext.requiredVenueTypeCode());
    assertEquals(15, approvedNext.readingTimeMinutes());
    assertEquals(120, approvedNext.durationMinutes());
    assertEquals("Calculators allowed", approvedNext.specialRequirements());
    assertEquals(
        Map.of(module, savedRequirements.get(next.id())), query.approvedRequirements(PERIOD));
    assertEquals(savedSlots, query.slots(session.id()));
    assertEquals(savedWindows, query.availabilityFor(savedVenues.get(venue.id())));
    assertEquals(List.copyOf(savedVenues.values()), query.activeVenues());
    var register = service.register();
    assertEquals(1, register.venueTypes().size());
    assertEquals(1, register.venues().size());
    assertEquals(approved, register.sessions().getFirst());
    assertEquals(2, register.requirements().size());
  }

  @Test
  void requirementVersionsAreScopedToBothModuleAndAcademicPeriodAndVenueTypeIsOptional() {
    UUID module = UUID.randomUUID();
    service.createRequirement(
        new CreateRequirement(
            UUID.randomUUID(), module, "CSC101", "Programming", 60, 0, null, null));
    service.createRequirement(requirement(UUID.randomUUID(), null));
    var requirement = service.createRequirement(requirement(module, null));
    assertEquals(1, requirement.requirementVersion());
    assertNull(requirement.requiredVenueTypeId());
    assertNull(requirement.requiredVenueTypeCode());
    service.approveRequirement(
        requirement.id(), new WorkflowDecision("Approved once only", 0), ACTOR);
    assertThrows(
        IllegalStateException.class,
        () ->
            service.approveRequirement(
                requirement.id(), new WorkflowDecision("Attempted repeated approval", 0), ACTOR));
    assertEquals(
        ModuleExamRequirement.Status.APPROVED, savedRequirements.get(requirement.id()).getStatus());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "venue-type",
        "venue",
        "slot-session",
        "approval-session",
        "requirement-type",
        "approval-requirement",
        "query-session"
      })
  void missingSetupReferencesFailWithExplicitLookupErrors(String missing) {
    UUID unknown = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          switch (missing) {
            case "venue-type" ->
                service.createVenue(
                    new CreateVenue(unknown, "GH", "Great Hall", "Main", null, null, 100, null));
            case "venue" ->
                service.addAvailability(
                    unknown, new AddAvailability(NOW, NOW.plusSeconds(60), null));
            case "slot-session" ->
                service.addSlot(unknown, new CreateSlot("AM", NOW, NOW.plusSeconds(60)));
            case "approval-session" ->
                service.approveSession(unknown, new WorkflowDecision("Approved", 0), ACTOR);
            case "requirement-type" ->
                service.createRequirement(requirement(UUID.randomUUID(), unknown));
            case "approval-requirement" ->
                service.approveRequirement(unknown, new WorkflowDecision("Approved", 0), ACTOR);
            case "query-session" -> query.requireApprovedSession(unknown);
            default -> fail("Unknown reference");
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"period", "assessment-type", "start", "end", "date-order"})
  void examSessionRejectsIncompleteScopeAndReversedDates(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExamSession(
                "period".equals(invalid) ? null : PERIOD,
                "2026-S2",
                "FINAL",
                "Final",
                "assessment-type".equals(invalid) ? null : ExamSession.AssessmentType.FINAL_EXAM,
                "start".equals(invalid) ? null : START,
                "end".equals(invalid)
                    ? null
                    : "date-order".equals(invalid) ? START.minusDays(1) : START));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null-start", "null-end", "equal", "reversed"})
  void slotAndVenueAvailabilityRejectNonpositiveWindows(String invalid) {
    Instant from = "null-start".equals(invalid) ? null : NOW;
    Instant until =
        switch (invalid) {
          case "null-end" -> null;
          case "equal" -> NOW;
          case "reversed" -> NOW.minusSeconds(1);
          default -> NOW.plusSeconds(60);
        };
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExamSessionSlot(draftSession(), "AM", from, until));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExamVenueAvailabilityWindow(null, from, until, null));
  }

  @Test
  void sessionApprovalFreezesSlotsAndRejectsStaleOrRepeatedDecisions() {
    ExamSession session = draftSession();
    assertThrows(IllegalStateException.class, () -> session.approve(ACTOR, "Approved", NOW, 1));
    assertEquals(ExamSession.Status.DRAFT, session.getStatus());
    session.approve(ACTOR, "Approved", NOW, 0);
    assertThrows(
        IllegalStateException.class, () -> session.approve(ACTOR, "Approved again", NOW, 0));
    assertThrows(
        IllegalStateException.class,
        () -> new ExamSessionSlot(session, "AM", NOW, NOW.plusSeconds(60)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "period",
        "module",
        "version",
        "duration-low",
        "duration-high",
        "reading-low",
        "reading-high"
      })
  void moduleRequirementsRejectInvalidScopeOrExaminationDurations(String invalid) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ModuleExamRequirement(
                "period".equals(invalid) ? null : PERIOD,
                "module".equals(invalid) ? null : UUID.randomUUID(),
                "CSC101",
                "Programming",
                "version".equals(invalid) ? 0 : 1,
                "duration-low".equals(invalid) ? 14 : "duration-high".equals(invalid) ? 481 : 60,
                "reading-low".equals(invalid) ? -1 : "reading-high".equals(invalid) ? 121 : 0,
                null,
                null));
  }

  @Test
  void requirementApprovalAndSupersessionHaveIndependentLifecycleAndVersionGuards() {
    var summary = service.createRequirement(requirement(UUID.randomUUID(), null));
    ModuleExamRequirement requirement = savedRequirements.get(summary.id());
    assertThrows(IllegalStateException.class, requirement::supersede);
    assertThrows(IllegalStateException.class, () -> requirement.approve(ACTOR, "Approved", NOW, 1));
    assertEquals(ModuleExamRequirement.Status.DRAFT, requirement.getStatus());
    requirement.approve(ACTOR, "Approved", NOW, 0);
    requirement.supersede();
    assertThrows(IllegalStateException.class, requirement::supersede);
    assertThrows(IllegalStateException.class, () -> requirement.approve(ACTOR, "Approved", NOW, 0));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void setupIdentifiersAndApprovalReasonsCannotBeMissing(String value) {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.createVenueType(new CreateVenueType(value, "Hall", null)));
    assertThrows(
        IllegalArgumentException.class, () -> draftSession().approve(ACTOR, value, NOW, 0));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void venueExaminationCapacityMustBePositive(int capacity) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExamVenue(
                new ExamVenueType("HALL", "Hall", null),
                "GH",
                "Hall",
                "Main",
                null,
                null,
                capacity,
                null));
  }

  @Test
  void optionalVenueDetailsAreNormalizedWithoutInventingMissingData() {
    var type = service.createVenueType(new CreateVenueType("HALL", "Hall", " "));
    var venue =
        service.createVenue(
            new CreateVenue(type.id(), "GH", "Great Hall", "Main", null, " ", 1, null));
    assertNull(type.description());
    assertNull(venue.buildingName());
    assertNull(venue.roomName());
    assertNull(venue.accessibilityNotes());
  }

  private CreateRequirement requirement(UUID module, UUID type) {
    return new CreateRequirement(
        PERIOD, module, "CSC101", "Programming", 120, 15, type, " Calculators allowed ");
  }

  private ExamSession draftSession() {
    return new ExamSession(
        PERIOD, "2026-S2", "FINAL", "Final", ExamSession.AssessmentType.FINAL_EXAM, START, START);
  }
}
