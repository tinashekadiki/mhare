package zw.ac.uz.emhare.coreidentity.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import zw.ac.uz.emhare.common.messaging.*;
import zw.ac.uz.emhare.coreidentity.provisioning.StudentPortalAccessProvisioningService;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.StudentPortalAccessProvisioning;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.*;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;

/**
 * @author Tinashe K
 */
class CoreWorkflowEventContractTest {
  private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
  private final ObjectMapper mapper = new ObjectMapper();
  private final CoreIdentityIntegrationInbox inbox = mock(CoreIdentityIntegrationInbox.class);
  private final WorkflowService workflows = mock(WorkflowService.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final StudentPortalAccessProvisioningService provisioning =
      mock(StudentPortalAccessProvisioningService.class);
  private final CoreIdentityIntegrationOutboxService outbox =
      mock(CoreIdentityIntegrationOutboxService.class);
  private final UUID eventId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private final UUID actorReference = UUID.randomUUID();
  private AcademicReviewWorkflowEventListener academic;
  private StudentPortalAccessProvisioningEventListener student;

  @BeforeEach
  void setup() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    academic =
        new AcademicReviewWorkflowEventListener(inbox, workflows, roles, users, mapper, clock);
    student =
        new StudentPortalAccessProvisioningEventListener(
            inbox, provisioning, outbox, mapper, clock);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void release_shouldProjectScopedTaskAndResolveLocalOrIdentityProviderActor(
      boolean externalReference) {
    PlatformUser actor = actor();
    Role role =
        new Role("ACADEMIC_UNIT_STAFF", "Academic Unit Staff", RoleScope.ACADEMIC_UNIT, true);
    ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
    when(roles.findByCode("ACADEMIC_UNIT_STAFF")).thenReturn(Optional.of(role));
    if (externalReference) {
      ReflectionTestUtils.setField(actor, "id", UUID.randomUUID());
      when(users.findByKeycloakUserId(actorReference)).thenReturn(Optional.of(actor));
    } else {
      when(users.findById(actorReference)).thenReturn(Optional.of(actor));
    }
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    Map<String, Object> payload = release();
    Message message = message(payload);
    academic.receiveRelease(message);
    ArgumentCaptor<CreateWorkflowCommand> command =
        ArgumentCaptor.forClass(CreateWorkflowCommand.class);
    var ordered = inOrder(inbox, workflows);
    ordered
        .verify(inbox)
        .claim(
            eventId,
            EmhareMessagingTopology.ACADEMIC_REVIEW_RELEASED_EVENT,
            "admissions-service",
            new String(message.getBody(), StandardCharsets.UTF_8),
            NOW);
    ordered.verify(workflows).createWorkflow(command.capture(), eq(actor.getId()));
    ordered.verify(inbox).markProcessed(eventId, NOW);
    assertAll(
        () -> assertEquals("ADMISSIONS_ACADEMIC_RECOMMENDATION", command.getValue().workflowCode()),
        () -> assertEquals(assignmentId, command.getValue().subjectId()),
        () -> assertEquals("EMH-001/CSC", command.getValue().subjectReference()),
        () -> assertEquals(role.getId(), command.getValue().assignedRoleId()),
        () -> assertNull(command.getValue().assignedUserId()),
        () -> assertEquals(WorkflowScopeType.ACADEMIC_UNIT, command.getValue().scopeType()),
        () ->
            assertEquals(
                payload.get("recommendationAcademicUnitId"), command.getValue().academicUnitId()),
        () -> assertEquals(NOW.plusSeconds(3600), command.getValue().dueAt()));
    if (!externalReference) verify(users, never()).findByKeycloakUserId(any());
  }

  @Test
  void recommendation_shouldCompleteAdmissionsOwnedSubjectBeforeAcknowledging() {
    PlatformUser actor = actor();
    when(users.findById(actorReference)).thenReturn(Optional.of(actor));
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    academic.receiveRecommendation(message(recommendation()));
    var ordered = inOrder(workflows, inbox);
    ordered
        .verify(workflows)
        .completeSubjectWorkflow(
            "ADMISSIONS_ACADEMIC_RECOMMENDATION",
            assignmentId,
            "RECOMMENDED",
            "Academic-unit recommendation recorded.",
            actor.getId());
    ordered.verify(inbox).markProcessed(eventId, NOW);
  }

  @ParameterizedTest
  @ValueSource(strings = {"schemaVersion", "assignmentId", "recommendationAcademicUnitId"})
  void release_shouldRejectInvalidContractBeforeClaim(String field) {
    Map<String, Object> payload = release();
    payload.put(field, field.equals("schemaVersion") ? 99 : null);
    assertThrows(IllegalArgumentException.class, () -> academic.receiveRelease(message(payload)));
    verifyNoInteractions(inbox, workflows, roles, users);
  }

  @ParameterizedTest
  @ValueSource(strings = {"schemaVersion", "assignmentId", "recommendedByUserId"})
  void recommendation_shouldRejectInvalidContractBeforeClaim(String field) {
    Map<String, Object> payload = recommendation();
    payload.put(field, field.equals("schemaVersion") ? 99 : null);
    assertThrows(
        IllegalArgumentException.class, () -> academic.receiveRecommendation(message(payload)));
    verifyNoInteractions(inbox, workflows, users);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void academicProjection_shouldIgnoreDuplicateReleaseAndRecommendation(boolean recommendation) {
    if (recommendation) academic.receiveRecommendation(message(recommendation()));
    else academic.receiveRelease(message(release()));
    verifyNoInteractions(workflows, roles, users);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void release_shouldNotAcknowledgeWhenAcademicRoleIsUnavailable() {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    assertEquals(
        "Academic Unit Staff role is not configured.",
        assertThrows(IllegalStateException.class, () -> academic.receiveRelease(message(release())))
            .getMessage());
    verifyNoInteractions(workflows, users);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void recommendation_shouldRejectMissingOrRetiredActorWithoutAcknowledging(boolean retired) {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    if (retired) {
      PlatformUser user = actor();
      user.markDeleted(UUID.randomUUID());
      when(users.findById(actorReference)).thenReturn(Optional.of(user));
    }
    assertEquals(
        "Workflow actor has not been synchronized with Core Identity.",
        assertThrows(
                IllegalArgumentException.class,
                () -> academic.receiveRecommendation(message(recommendation())))
            .getMessage());
    verifyNoInteractions(workflows);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void recommendation_shouldPropagateWorkflowFailureWithoutAcknowledging() {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    PlatformUser actor = actor();
    when(users.findById(actorReference)).thenReturn(Optional.of(actor));
    doThrow(new IllegalStateException("Queue is no longer authorised"))
        .when(workflows)
        .completeSubjectWorkflow(any(), any(), any(), any(), any());
    assertEquals(
        "Queue is no longer authorised",
        assertThrows(
                IllegalStateException.class,
                () -> academic.receiveRecommendation(message(recommendation())))
            .getMessage());
    verify(inbox, never()).markProcessed(any(), any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "schemaVersion",
        "conversionRequestId",
        "studentId",
        "userId",
        "studentNumber",
        "blankNumber"
      })
  void studentAccess_shouldRejectIncompleteConversionContract(String field) {
    Map<String, Object> payload = studentRequest();
    payload.put(
        field.equals("blankNumber") ? "studentNumber" : field,
        field.equals("schemaVersion") ? 2 : field.equals("blankNumber") ? " " : null);
    assertThrows(IllegalArgumentException.class, () -> student.receive(message(payload)));
    verifyNoInteractions(inbox, provisioning, outbox);
  }

  @Test
  void studentAccess_shouldPublishActualProvisioningAndAcknowledgeInOrder() {
    var request =
        mapper.convertValue(studentRequest(), StudentPortalAccessProvisioningRequestedEvent.class);
    PlatformUser user = actor();
    var access =
        new StudentPortalAccessProvisioning(
            request,
            user,
            new UserRoleAssignment(
                user, new Role("STUDENT", "Student", RoleScope.SYSTEM, true), null, NOW),
            NOW);
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    when(provisioning.ensureAccess(request)).thenReturn(access);
    student.receive(message(request));
    var ordered = inOrder(inbox, provisioning, outbox);
    ordered
        .verify(inbox)
        .claim(
            eq(eventId),
            eq(EmhareMessagingTopology.STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT),
            eq("student-records-service"),
            anyString(),
            eq(NOW));
    ordered.verify(provisioning).ensureAccess(request);
    ordered.verify(outbox).enqueueStudentPortalAccessProvisioned(access);
    ordered.verify(inbox).markProcessed(eventId, NOW);
  }

  @Test
  void studentAccess_shouldIgnoreDuplicateWithoutProvisioningAgain() {
    student.receive(message(studentRequest()));
    verifyNoInteractions(provisioning, outbox);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void studentAccess_shouldLeaveFailedProvisioningUnacknowledged() {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    when(provisioning.ensureAccess(any()))
        .thenThrow(new IllegalStateException("Conversion conflict"));
    assertEquals(
        "Conversion conflict",
        assertThrows(IllegalStateException.class, () -> student.receive(message(studentRequest())))
            .getMessage());
    verifyNoInteractions(outbox);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void malformedJson_shouldBeRejectedByBothListeners() {
    Message invalid = new Message("not-json".getBytes(StandardCharsets.UTF_8));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> academic.receiveRelease(invalid))
            .getMessage()
            .contains("event is invalid"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> student.receive(invalid))
            .getMessage()
            .contains("event is invalid"));
    verifyNoInteractions(inbox, workflows, provisioning);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "schemaVersion",
        "applicationId",
        "applicationNumber",
        "blankApplication",
        "applicantUserId",
        "documentId",
        "documentVersion",
        "requirementCode",
        "blankRequirement",
        "rejectionReason",
        "blankReason",
        "initiatedByUserId"
      })
  void documentCorrection_shouldRejectMissingEvidenceBeforeCreatingApplicantTask(String field) {
    Map<String, Object> payload = documentCorrection();
    switch (field) {
      case "schemaVersion" -> payload.put(field, 99);
      case "documentVersion" -> payload.put(field, -1);
      case "blankApplication" -> payload.put("applicationNumber", " ");
      case "blankRequirement" -> payload.put("requirementCode", " ");
      case "blankReason" -> payload.put("rejectionReason", " ");
      default -> payload.put(field, null);
    }
    assertThrows(
        IllegalArgumentException.class, () -> documentListener().receive(message(payload)));
    verifyNoInteractions(inbox, workflows, users);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void documentCorrection_shouldRejectUnresolvedOrRetiredReviewer(boolean retired) {
    when(inbox.claim(any(), any(), any(), any(), any())).thenReturn(true);
    if (retired) {
      PlatformUser user = actor();
      user.markDeleted(UUID.randomUUID());
      when(users.findById(actorReference)).thenReturn(Optional.of(user));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> documentListener().receive(message(documentCorrection())));
    verifyNoInteractions(workflows);
    verify(inbox, never()).markProcessed(any(), any());
  }

  @Test
  void documentCorrection_shouldExplainMalformedJson() {
    assertEquals(
        "Missing-document workflow event is invalid.",
        assertThrows(
                IllegalArgumentException.class,
                () -> documentListener().receive(new Message("[".getBytes(StandardCharsets.UTF_8))))
            .getMessage());
    verifyNoInteractions(inbox, workflows);
  }

  private MissingApplicationDocumentWorkflowRequestedEventListener documentListener() {
    return new MissingApplicationDocumentWorkflowRequestedEventListener(
        inbox, workflows, users, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Map<String, Object> documentCorrection() {
    Map<String, Object> value = base();
    value.put("applicationId", UUID.randomUUID());
    value.put("applicationNumber", "EMH-001");
    value.put("applicantUserId", UUID.randomUUID());
    value.put("documentId", UUID.randomUUID());
    value.put("documentVersion", 2);
    value.put("requirementCode", "NATIONAL_ID");
    value.put("rejectionReason", "Image is unreadable");
    value.put("initiatedByUserId", actorReference);
    value.put("dueAt", NOW.plusSeconds(3600));
    return value;
  }

  private PlatformUser actor() {
    PlatformUser user =
        new PlatformUser(actorReference, "reviewer", "reviewer@example.test", "Reviewer");
    ReflectionTestUtils.setField(user, "id", actorReference);
    user.activate();
    return user;
  }

  private Map<String, Object> base() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("eventId", eventId);
    value.put("schemaVersion", 1);
    value.put("occurredAt", NOW);
    return value;
  }

  private Map<String, Object> release() {
    Map<String, Object> value = base();
    value.put("assignmentId", assignmentId);
    value.put("applicationId", UUID.randomUUID());
    value.put("applicationNumber", "EMH-001");
    value.put("programmeChoiceId", UUID.randomUUID());
    value.put("programmeCode", "CSC");
    value.put("programmeName", "Computing");
    value.put("recommendationAcademicUnitId", UUID.randomUUID());
    value.put("recommendationAcademicUnitCode", "SCI");
    value.put("recommendationAcademicUnitName", "Science");
    value.put("releasedByUserId", actorReference);
    value.put("dueAt", NOW.plusSeconds(3600));
    return value;
  }

  private Map<String, Object> recommendation() {
    Map<String, Object> value = base();
    value.put("assignmentId", assignmentId);
    value.put("recommendationId", UUID.randomUUID());
    value.put("recommendation", "RECOMMENDED");
    value.put("recommendedByUserId", actorReference);
    value.put("recommendedAt", NOW);
    return value;
  }

  private Map<String, Object> studentRequest() {
    Map<String, Object> value = base();
    value.put("conversionRequestId", UUID.randomUUID());
    value.put("studentId", UUID.randomUUID());
    value.put("studentNumber", "R260001");
    value.put("userId", actorReference);
    return value;
  }

  private Message message(Object value) {
    return new Message(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
  }
}
