package zw.ac.uz.emhare.studentrecords.registration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.messaging.AcceptedOfferReadyForConversionEvent;
import zw.ac.uz.emhare.studentrecords.conversion.StudentSelfServiceService;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProgrammeEnrolment;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProfileRepository;
import zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence.StudentProgrammeEnrolmentRepository;
import zw.ac.uz.emhare.studentrecords.integration.StudentRecordsIntegrationOutboxService;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationModuleOption;
import zw.ac.uz.emhare.studentrecords.registration.api.model.RegistrationRequests.CreateOwnRegistration;
import zw.ac.uz.emhare.studentrecords.registration.api.model.RegistrationRequests.CreateRegistration;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;
import zw.ac.uz.emhare.studentrecords.registration.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class StudentRegistrationGovernanceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final UUID actor = UUID.randomUUID(), periodId = UUID.randomUUID();
  private StudentProfileRepository students;
  private StudentProgrammeEnrolmentRepository enrolments;
  private RegistrationSessionRepository registrations;
  private RegistrationModuleRepository modules;
  private RegistrationStatusEventRepository events;
  private AcademicRegistrationCatalogueClient academic;
  private StudentRecordsIntegrationOutboxService outbox;
  private StudentRegistrationService service;
  private StudentProfile student;
  private StudentProgrammeEnrolment enrolment;
  private RegistrationSession registration;
  private RegistrationCatalogue catalogue;
  private RegistrationModuleOption compulsory, elective;

  @BeforeEach
  void setUp() {
    students = mock(StudentProfileRepository.class);
    enrolments = mock(StudentProgrammeEnrolmentRepository.class);
    registrations = mock(RegistrationSessionRepository.class);
    modules = mock(RegistrationModuleRepository.class);
    events = mock(RegistrationStatusEventRepository.class);
    academic = mock(AcademicRegistrationCatalogueClient.class);
    outbox = mock(StudentRecordsIntegrationOutboxService.class);
    service =
        new StudentRegistrationService(
            students,
            enrolments,
            registrations,
            modules,
            events,
            academic,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC));
    AcceptedOfferReadyForConversionEvent offer = offer(actor);
    student = new StudentProfile("R260001", offer);
    persisted(student);
    student.activate(NOW);
    enrolment = new StudentProgrammeEnrolment(student, offer);
    persisted(enrolment);
    enrolment.activate(NOW);
    compulsory = module("CSC101", "COMPULSORY", 1, BigDecimal.valueOf(12));
    elective = module("CSC201", "ELECTIVE", 2, BigDecimal.valueOf(18));
    catalogue =
        catalogue(
            enrolment.getProgrammeVersionId(),
            enrolment.getProgrammeId(),
            List.of(compulsory, elective));
    registration =
        new RegistrationSession(
            student.getStudentNumber(),
            student,
            enrolment,
            catalogue,
            RegistrationType.NORMAL,
            NOW);
    persisted(registration);
    when(students.findByIdAndDeletedAtIsNull(student.getId())).thenReturn(Optional.of(student));
    when(students.findByUserIdAndDeletedAtIsNull(actor)).thenReturn(Optional.of(student));
    when(enrolments.findByIdAndDeletedAtIsNull(enrolment.getId()))
        .thenReturn(Optional.of(enrolment));
    when(registrations.findByIdAndDeletedAtIsNull(registration.getId()))
        .thenReturn(Optional.of(registration));
    when(registrations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(academic.getRegistrationCatalogue(periodId, enrolment.getProgrammeVersionId(), 1))
        .thenAnswer(invocation -> catalogue);
    when(modules.findAllByRegistrationSessionIdOrderBySortOrderAsc(registration.getId()))
        .thenReturn(
            List.of(
                new RegistrationModule(
                    registration, compulsory, ModuleSelectionSource.AUTO_COMPULSORY)));
  }

  @Test
  void staffRegistrationUsesAuthoritativeCurriculumAndCapturesSelectionProvenance() {
    RegistrationSummary result =
        service.create(command(Set.of(elective.curriculumModuleId())), actor);
    assertEquals(RegistrationStatus.DRAFT, result.status());
    assertEquals("R260001", result.registrationNumber());
    assertEquals(new BigDecimal("30"), result.totalCredits());
    assertEquals(
        List.of(ModuleSelectionSource.AUTO_COMPULSORY, ModuleSelectionSource.STAFF_ELECTIVE),
        result.modules().stream()
            .map(RegistrationSummary.RegisteredModuleSummary::selectionSource)
            .toList());
    assertEquals(
        List.of(compulsory.curriculumModuleId(), elective.curriculumModuleId()),
        result.modules().stream()
            .map(RegistrationSummary.RegisteredModuleSummary::curriculumModuleId)
            .toList());
    verify(events).save(any(RegistrationStatusEvent.class));
    verifyNoInteractions(outbox);
  }

  @Test
  void studentRegistrationResolvesIdentityFromActorAndDoesNotSelectUnrequestedElectives() {
    RegistrationSummary result =
        service.createForUser(
            new CreateOwnRegistration(enrolment.getId(), periodId, 1, Set.of()), actor);
    assertEquals(student.getId(), result.studentId());
    assertEquals(1, result.modules().size());
    assertEquals(
        ModuleSelectionSource.AUTO_COMPULSORY, result.modules().getFirst().selectionSource());
    verify(students).findByUserIdAndDeletedAtIsNull(actor);
    verify(students, never()).findByIdAndDeletedAtIsNull(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "student-missing",
        "enrolment-missing",
        "wrong-student",
        "inactive-student",
        "inactive-enrolment",
        "duplicate",
        "wrong-programme",
        "wrong-version",
        "foreign-elective",
        "no-modules"
      })
  void invalidRegistrationCannotCreatePartialRecordsOrEvents(String failure) {
    Set<UUID> selected = Set.of();
    switch (failure) {
      case "student-missing" ->
          when(students.findByIdAndDeletedAtIsNull(student.getId())).thenReturn(Optional.empty());
      case "enrolment-missing" ->
          when(enrolments.findByIdAndDeletedAtIsNull(enrolment.getId()))
              .thenReturn(Optional.empty());
      case "wrong-student" -> {
        StudentProfile other = new StudentProfile("R260002", offer(UUID.randomUUID()));
        persisted(other);
        when(enrolments.findByIdAndDeletedAtIsNull(enrolment.getId()))
            .thenReturn(Optional.of(new StudentProgrammeEnrolment(other, offer(actor))));
      }
      case "inactive-student" -> {
        StudentProfile pending = new StudentProfile("R260003", offer(actor));
        when(students.findByIdAndDeletedAtIsNull(student.getId())).thenReturn(Optional.of(pending));
      }
      case "inactive-enrolment" ->
          when(enrolments.findByIdAndDeletedAtIsNull(enrolment.getId()))
              .thenReturn(Optional.of(new StudentProgrammeEnrolment(student, offer(actor))));
      case "duplicate" ->
          when(registrations.existsByStudentIdAndAcademicPeriodIdAndStatusNotAndDeletedAtIsNull(
                  student.getId(), periodId, RegistrationStatus.CANCELLED))
              .thenReturn(true);
      case "wrong-programme" ->
          catalogue =
              catalogue(enrolment.getProgrammeVersionId(), UUID.randomUUID(), List.of(compulsory));
      case "wrong-version" ->
          catalogue = catalogue(UUID.randomUUID(), enrolment.getProgrammeId(), List.of(compulsory));
      case "foreign-elective" -> selected = Set.of(UUID.randomUUID());
      case "no-modules" ->
          catalogue =
              catalogue(
                  enrolment.getProgrammeVersionId(), enrolment.getProgrammeId(), List.of(elective));
      default -> throw new AssertionError(failure);
    }
    CreateRegistration request = command(selected);
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> service.create(request, actor));
    String expectedMessage =
        switch (failure) {
          case "student-missing" -> "Student was not found.";
          case "enrolment-missing" -> "Student programme enrolment was not found.";
          case "wrong-student" -> "Programme enrolment does not belong to the selected student.";
          case "inactive-student" -> "Only an active student can start registration.";
          case "inactive-enrolment" -> "Only an active programme enrolment can be registered.";
          case "duplicate" -> "The student already has a registration for this academic period.";
          case "wrong-programme", "wrong-version" ->
              "Academic catalogue does not match the student's programme enrolment.";
          case "foreign-elective" ->
              "One or more selected electives are not in the approved curriculum period.";
          case "no-modules" -> "Registration must contain at least one approved curriculum Module.";
          default -> throw new AssertionError(failure);
        };
    assertEquals(expectedMessage, exception.getMessage());
    verify(registrations, never()).saveAndFlush(any());
    verifyNoInteractions(modules, events, outbox);
  }

  @Test
  void confirmedRegistrationAlonePublishesTheDownstreamAcademicRoster() {
    RegistrationSummary submitted =
        service.submit(registration.getId(), 0, "  Staff submission evidence  ", actor);
    assertEquals(RegistrationStatus.SUBMITTED, submitted.status());
    assertEquals(NOW, submitted.submittedAt());
    verify(outbox, never()).enqueueRegistrationConfirmed(any(), any());
    RegistrationSummary approved =
        service.approveAcademically(registration.getId(), 0, "Curriculum checked", actor);
    assertEquals(RegistrationStatus.ACADEMIC_APPROVED, approved.status());
    assertEquals(NOW, approved.academicApprovedAt());
    verify(outbox, never()).enqueueRegistrationConfirmed(any(), any());
    RegistrationSummary confirmed =
        service.confirm(registration.getId(), 0, "Registry verified", UUID.randomUUID());
    assertEquals(RegistrationStatus.CONFIRMED, confirmed.status());
    assertEquals(NOW, confirmed.confirmedAt());
    verify(outbox)
        .enqueueRegistrationConfirmed(
            eq(registration),
            argThat(
                list ->
                    list.size() == 1
                        && list.getFirst()
                            .getCurriculumModuleId()
                            .equals(compulsory.curriculumModuleId())));
    verify(events, times(3)).save(any(RegistrationStatusEvent.class));
    verify(outbox, times(3)).enqueueRegistrationActionNotification(eq(registration), anyString());
  }

  @Test
  void ownSubmissionRecordsSelfServiceEvidenceAndConfirmationRequiresModules() {
    RegistrationSummary result = service.submitForUser(registration.getId(), 0, actor);
    assertEquals("Submitted by the student through self-service.", result.statusReason());
    service.approveAcademically(registration.getId(), 0, "Reviewed", actor);
    when(modules.findAllByRegistrationSessionIdOrderBySortOrderAsc(registration.getId()))
        .thenReturn(List.of());
    assertThrows(
        IllegalStateException.class,
        () -> service.confirm(registration.getId(), 0, "Checked", actor));
    assertEquals(RegistrationStatus.ACADEMIC_APPROVED, registration.getStatus());
    verify(outbox, never()).enqueueRegistrationConfirmed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectedAcademicOrRegistryDecisionRetainsReasonAndNeverPublishesRoster(boolean approved) {
    registration.submit("Ready", NOW, 0);
    if (approved) registration.approveAcademically(actor, "Academic review", NOW, 0);
    RegistrationSummary result =
        service.reject(registration.getId(), 0, "  Missing evidence  ", actor);
    assertEquals(RegistrationStatus.REJECTED, result.status());
    assertEquals("Missing evidence", result.statusReason());
    verify(outbox)
        .enqueueRegistrationActionNotification(eq(registration), contains("Missing evidence"));
    verify(outbox, never()).enqueueRegistrationConfirmed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"submit", "approve", "confirm", "reject"})
  void staleDecisionVersionsDoNotProduceAuditOrIntegrationWrites(String action) {
    if (!action.equals("submit")) registration.submit("Ready for review", NOW, 0);
    if (action.equals("confirm")) registration.approveAcademically(actor, "Reviewed", NOW, 0);
    RegistrationStatus previousStatus = registration.getStatus();
    String previousReason = registration.getStatusReason();
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> {
              switch (action) {
                case "submit" -> service.submit(registration.getId(), 99, "Reason", actor);
                case "approve" ->
                    service.approveAcademically(registration.getId(), 99, "Reason", actor);
                case "confirm" -> service.confirm(registration.getId(), 99, "Reason", actor);
                case "reject" -> service.reject(registration.getId(), 99, "Reason", actor);
                default -> throw new AssertionError(action);
              }
            });
    assertEquals(
        "Registration was changed by another user. Refresh before retrying.", error.getMessage());
    assertEquals(previousStatus, registration.getStatus());
    assertEquals(previousReason, registration.getStatusReason());
    assertNull(registration.getConfirmedAt());
    verifyNoInteractions(events, outbox);
    verify(registrations, never()).saveAndFlush(any());
  }

  @Test
  void missingIdentitiesAndRegistrationsFailClosed() {
    when(students.findByUserIdAndDeletedAtIsNull(actor)).thenReturn(Optional.empty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.createForUser(
                new CreateOwnRegistration(enrolment.getId(), periodId, 1, Set.of()), actor));
    assertThrows(IllegalArgumentException.class, () -> service.listForUser(actor));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.submit(UUID.randomUUID(), 0, "Reason", actor));
    verifyNoInteractions(outbox, events);
  }

  @Test
  void authenticatedStudentCannotSubmitAnotherStudentsRegistration() {
    StudentProfile other = new StudentProfile("R260002", offer(actor));
    persisted(other);
    when(students.findByUserIdAndDeletedAtIsNull(actor)).thenReturn(Optional.of(other));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.submitForUser(registration.getId(), 0, actor));
    assertEquals(RegistrationStatus.DRAFT, registration.getStatus());
    verifyNoInteractions(outbox, events);
  }

  @Test
  void staffAndStudentRegistersUseDistinctRepositoryScopesAndReturnSameAuthoritativeEvidence() {
    when(registrations.findAllByDeletedAtIsNullOrderByInitiatedAtDesc())
        .thenReturn(List.of(registration));
    when(registrations.findAllByStudentIdAndDeletedAtIsNullOrderByInitiatedAtDesc(student.getId()))
        .thenReturn(List.of(registration));
    assertEquals(service.list(), service.listForUser(actor));
    verify(registrations)
        .findAllByStudentIdAndDeletedAtIsNullOrderByInitiatedAtDesc(student.getId());
    assertEquals("Tariro Moyo", service.list().getFirst().studentName());
  }

  @Test
  void selfServiceWorkspaceProjectsLinkedStudentAndEnrolmentWithoutCrossStudentLookup() {
    when(enrolments.findAllByStudentIdAndDeletedAtIsNullOrderByCommencementDateDesc(
            student.getId()))
        .thenReturn(List.of(enrolment));
    var workspace = new StudentSelfServiceService(students, enrolments).workspaceForUser(actor);
    assertEquals(student.getStudentNumber(), workspace.studentNumber());
    assertEquals(1, workspace.programmeEnrolments().size());
    assertEquals(
        enrolment.getProgrammeVersionId(),
        workspace.programmeEnrolments().getFirst().programmeVersionId());
    when(students.findByUserIdAndDeletedAtIsNull(actor)).thenReturn(Optional.empty());
    assertThrows(
        IllegalArgumentException.class,
        () -> new StudentSelfServiceService(students, enrolments).workspaceForUser(actor));
  }

  private CreateRegistration command(Set<UUID> selected) {
    return new CreateRegistration(
        student.getId(), enrolment.getId(), periodId, 1, RegistrationType.NORMAL, selected);
  }

  private RegistrationCatalogue catalogue(
      UUID versionId, UUID programmeId, List<RegistrationModuleOption> options) {
    return new RegistrationCatalogue(
        periodId,
        "2026-S1",
        "Semester one",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 12, 31),
        versionId,
        programmeId,
        "BSC",
        "Computing",
        "2026.1",
        UUID.randomUUID(),
        "SCI",
        "Science",
        UUID.randomUUID(),
        "UG",
        "Undergraduate",
        1,
        options);
  }

  private RegistrationModuleOption module(String code, String type, int order, BigDecimal credits) {
    return new RegistrationModuleOption(
        UUID.randomUUID(),
        UUID.randomUUID(),
        code,
        code + " module",
        type,
        credits,
        BigDecimal.valueOf(50),
        order);
  }

  private static void persisted(Object entity) {
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
  }

  private static AcceptedOfferReadyForConversionEvent offer(UUID userId) {
    return new AcceptedOfferReadyForConversionEvent(
        UUID.randomUUID(),
        AcceptedOfferReadyForConversionEvent.CURRENT_SCHEMA_VERSION,
        NOW,
        UUID.randomUUID(),
        "APP-2026-1",
        UUID.randomUUID(),
        "OFR-2026-1",
        UUID.randomUUID(),
        userId,
        "APL-2026-1",
        "LOCAL",
        "Tariro",
        "Moyo",
        "tariro@example.test",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BSC",
        "Computing",
        UUID.randomUUID(),
        LocalDate.of(2026, 8, 1));
  }
}
