package zw.ac.uz.emhare.coreidentity.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.coreidentity.integration.CoreIdentityIntegrationOutboxService;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.RoleScope;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserRoleAssignment;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.UserRoleAssignmentRepository;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowTaskCommand;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.WorkflowDecisionCommand;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence.*;

/**
 * @author Tinashe K
 */
class WorkflowServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");
  private static final UUID ACTOR_ID = new UUID(0, 1);
  private static final UUID WORKFLOW_ID = new UUID(0, 2);
  private static final UUID SUBJECT_ID = new UUID(0, 3);
  private static final UUID TASK_ID = new UUID(0, 4);
  private static final UUID ROLE_ID = new UUID(0, 5);
  private static final UUID UNIT_ID = new UUID(0, 6);
  private static final List<WorkflowTaskStatus> ACTIONABLE =
      List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED);

  private final WorkflowInstanceRepository workflows = mock(WorkflowInstanceRepository.class);
  private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
  private final WorkflowDecisionRepository decisions = mock(WorkflowDecisionRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
  private final CoreIdentityIntegrationOutboxService outbox =
      mock(CoreIdentityIntegrationOutboxService.class);
  private final WorkflowService service =
      new WorkflowService(
          workflows,
          tasks,
          decisions,
          users,
          roles,
          assignments,
          outbox,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final List<WorkflowTask> storedTasks = new ArrayList<>();
  private final List<WorkflowDecision> storedDecisions = new ArrayList<>();
  private PlatformUser actor;
  private WorkflowInstance workflow;

  @BeforeEach
  void setUp() {
    actor = user(ACTOR_ID, true);
    workflow =
        identify(
            new WorkflowInstance(
                "ADMISSION_REVIEW",
                "APPLICATION",
                SUBJECT_ID,
                "APP-001",
                "Review application",
                ACTOR_ID,
                NOW),
            WORKFLOW_ID);
    when(users.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
    when(workflows.findByIdAndDeletedAtIsNull(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
    when(workflows.saveAndFlush(any()))
        .thenAnswer(invocation -> identify(invocation.getArgument(0), WORKFLOW_ID));
    when(tasks.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              WorkflowTask task = invocation.getArgument(0);
              if (task.getId() == null) identify(task, TASK_ID);
              if (!storedTasks.contains(task)) storedTasks.add(task);
              return task;
            });
    when(tasks.findAllByWorkflowInstanceIdOrderByCreatedAtAsc(WORKFLOW_ID))
        .thenAnswer(invocation -> List.copyOf(storedTasks));
    when(tasks.findAccessibleQueue(ACTOR_ID, ACTIONABLE, NOW))
        .thenAnswer(invocation -> List.copyOf(storedTasks));
    when(decisions.save(any()))
        .thenAnswer(
            invocation -> {
              WorkflowDecision decision = identify(invocation.getArgument(0), new UUID(0, 7));
              storedDecisions.add(decision);
              return decision;
            });
    when(decisions.findAllByWorkflowTaskIdOrderByDecidedAtAsc(TASK_ID))
        .thenAnswer(invocation -> List.copyOf(storedDecisions));
  }

  @Test
  void createsAnAuditableUserAssignedWorkflowAndNotifiesOnlyItsAssignee() {
    WorkflowInstanceSummary created =
        service.createWorkflow(
            new CreateWorkflowCommand(
                "admission_review",
                "application",
                SUBJECT_ID,
                "APP-001",
                "Review application",
                "Verify evidence",
                "Check certificates",
                ACTOR_ID,
                null,
                WorkflowScopeType.INSTITUTION,
                null,
                NOW.plusSeconds(3600)),
            ACTOR_ID);
    assertAll(
        () -> assertEquals(WorkflowStatus.ACTIVE, created.status()),
        () -> assertEquals("ADMISSION_REVIEW", created.workflowCode()),
        () -> assertEquals(ACTOR_ID, created.initiatedByUserId()),
        () -> assertEquals(NOW, created.initiatedAt()),
        () -> assertEquals(SUBJECT_ID, created.subjectId()),
        () -> assertEquals(1, created.tasks().size()));
    WorkflowTaskSummary task = created.tasks().getFirst();
    assertEquals(WorkflowTaskStatus.OPEN, task.status());
    assertEquals(ACTOR_ID, task.assignedUserId());
    assertNull(task.assignedRoleId());
    assertEquals(NOW.plusSeconds(3600), task.dueAt());
    assertTrue(task.taskReference().matches("WT-20260830-[0-9A-Fa-f]{8}"));
    verify(outbox).enqueueWorkflowTaskNotifications(storedTasks.getFirst(), List.of(actor));
    verifyNoInteractions(assignments);
  }

  @ParameterizedTest
  @EnumSource(WorkflowScopeType.class)
  void roleNotificationsRespectScopeDeduplicateRecipientsAndExcludeInactiveUsers(
      WorkflowScopeType scope) {
    UUID unitId = scope == WorkflowScopeType.ACADEMIC_UNIT ? UNIT_ID : null;
    Role role =
        identify(new Role("REVIEWER", "Admissions reviewer", RoleScope.SYSTEM, false), ROLE_ID);
    PlatformUser inactive = user(new UUID(0, 8), false);
    when(roles.findById(ROLE_ID)).thenReturn(Optional.of(role));
    when(assignments.findActiveRecipientsForRole(ROLE_ID, unitId, NOW))
        .thenReturn(
            List.of(
                new UserRoleAssignment(actor, role, unitId, NOW),
                new UserRoleAssignment(actor, role, unitId, NOW),
                new UserRoleAssignment(inactive, role, unitId, NOW)));
    WorkflowTaskSummary result =
        service.addTask(
            WORKFLOW_ID,
            new CreateWorkflowTaskCommand(
                "Verify evidence", "Check certificates", null, ROLE_ID, scope, unitId, null));
    assertEquals(ROLE_ID, result.assignedRoleId());
    assertEquals("Admissions reviewer", result.assignedRoleName());
    assertNull(result.assignedUserId());
    assertEquals(unitId, result.academicUnitId());
    verify(assignments).findActiveRecipientsForRole(ROLE_ID, unitId, NOW);
    verify(outbox).enqueueWorkflowTaskNotifications(storedTasks.getFirst(), List.of(actor));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "deleted", "inactive"})
  void deniesQueueAccessToMissingDeletedOrInactiveUsers(String condition) {
    if (condition.equals("missing")) when(users.findById(ACTOR_ID)).thenReturn(Optional.empty());
    if (condition.equals("deleted")) actor.markDeleted(ACTOR_ID);
    if (condition.equals("inactive"))
      when(users.findById(ACTOR_ID)).thenReturn(Optional.of(user(ACTOR_ID, false)));
    RuntimeException error =
        assertThrows(RuntimeException.class, () -> service.listMyQueue(ACTOR_ID));
    assertEquals(
        condition.equals("inactive")
            ? "Workflow actions require an active user."
            : "Workflow user was not found.",
        error.getMessage());
    verifyNoInteractions(tasks, decisions, outbox);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsMissingAndDeletedAssignedRoles(boolean deleted) {
    Role role = identify(new Role("REVIEWER", "Reviewer", RoleScope.SYSTEM, false), ROLE_ID);
    if (deleted) role.markDeleted(ACTOR_ID);
    when(roles.findById(ROLE_ID)).thenReturn(deleted ? Optional.of(role) : Optional.empty());
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.addTask(
                    WORKFLOW_ID,
                    new CreateWorkflowTaskCommand(
                        "Review",
                        "Evidence",
                        null,
                        ROLE_ID,
                        WorkflowScopeType.INSTITUTION,
                        null,
                        null)));
    assertEquals("Assigned role was not found.", error.getMessage());
    verify(tasks, never()).saveAndFlush(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void refusesTasksForMissingOrCompletedWorkflows() {
    assertThrows(
        IllegalArgumentException.class, () -> service.addTask(new UUID(0, 99), userTask()));
    assertThrows(IllegalArgumentException.class, () -> service.getWorkflow(new UUID(0, 99)));
    workflow.complete(NOW);
    assertThrows(IllegalStateException.class, () -> service.addTask(WORKFLOW_ID, userTask()));
    verify(tasks, never()).saveAndFlush(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void claimsAnAccessibleTaskAndProjectsOwnershipInQueueAndWorkflowViews() {
    WorkflowTask task = addUserTask();
    WorkflowTaskSummary claimed = service.claimTask(TASK_ID, 0, ACTOR_ID);
    assertEquals(WorkflowTaskStatus.CLAIMED, claimed.status());
    assertEquals(ACTOR_ID, claimed.claimedByUserId());
    assertEquals(NOW, claimed.claimedAt());
    assertNull(claimed.completedByUserId());
    assertEquals(List.of(claimed), service.listMyQueue(ACTOR_ID));
    when(tasks.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(task));
    assertEquals(List.of(claimed), service.listAllTasks());
    assertEquals(List.of(claimed), service.getWorkflow(WORKFLOW_ID).tasks());
  }

  @Test
  void rejectsTasksOutsideTheAuthorisedQueueAndStaleVersionsWithoutDecisions() {
    WorkflowTask task = addUserTask();
    clearInvocations(tasks);
    assertThrows(
        IllegalArgumentException.class, () -> service.claimTask(new UUID(0, 99), 0, ACTOR_ID));
    assertThrows(IllegalStateException.class, () -> service.claimTask(TASK_ID, 99, ACTOR_ID));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.decideTask(
                TASK_ID, new WorkflowDecisionCommand(0, "APPROVE", "Evidence verified"), ACTOR_ID));
    assertEquals(WorkflowTaskStatus.OPEN, task.getStatus());
    verify(tasks, never()).saveAndFlush(any());
    verify(decisions, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completesTheWorkflowOnlyAfterItsLastActionableTask(boolean anotherTaskRemains) {
    WorkflowTask task = addUserTask();
    task.claim(actor, NOW.minusSeconds(60), 0);
    when(tasks.existsByWorkflowInstanceIdAndStatusIn(WORKFLOW_ID, ACTIONABLE))
        .thenReturn(anotherTaskRemains);
    WorkflowTaskSummary result =
        service.decideTask(
            TASK_ID,
            new WorkflowDecisionCommand(0, " approve ", " Verified certificates "),
            ACTOR_ID);
    assertEquals(WorkflowTaskStatus.COMPLETED, result.status());
    assertEquals(ACTOR_ID, result.completedByUserId());
    assertEquals(NOW, result.completedAt());
    assertEquals("APPROVE", result.decisions().getFirst().decisionCode());
    assertEquals("Verified certificates", result.decisions().getFirst().comment());
    assertEquals(ACTOR_ID, result.decisions().getFirst().actorUserId());
    assertEquals(NOW, result.decisions().getFirst().decidedAt());
    assertEquals(
        anotherTaskRemains ? WorkflowStatus.ACTIVE : WorkflowStatus.COMPLETED,
        workflow.getStatus());
    verify(workflows, times(anotherTaskRemains ? 0 : 1)).save(workflow);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completesTheLatestSubjectWorkflowWithAnAuditedDecision(boolean alreadyClaimed) {
    WorkflowTask task = addUserTask();
    if (alreadyClaimed) task.claim(actor, NOW.minusSeconds(60), 0);
    subjectWorkflowExists();
    clearInvocations(tasks);
    service.completeSubjectWorkflow(
        "ADMISSION_REVIEW", SUBJECT_ID, "APPROVE", "Evidence verified", ACTOR_ID);
    assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    assertEquals(WorkflowTaskStatus.COMPLETED, task.getStatus());
    assertEquals(NOW, workflow.getCompletedAt());
    assertEquals(task, storedDecisions.getFirst().getWorkflowTask());
    verify(tasks, times(alreadyClaimed ? 1 : 2)).saveAndFlush(task);
    verify(workflows).save(workflow);
  }

  @Test
  void refusesMissingEmptyOrInaccessibleSubjectWorkflows() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.completeSubjectWorkflow(
                "ADMISSION_REVIEW", SUBJECT_ID, "APPROVE", "Evidence verified", ACTOR_ID));
    subjectWorkflowExists();
    assertThrows(
        IllegalStateException.class,
        () ->
            service.completeSubjectWorkflow(
                "ADMISSION_REVIEW", SUBJECT_ID, "APPROVE", "Evidence verified", ACTOR_ID));
    WorkflowTask task = addUserTask();
    when(tasks.findAccessibleQueue(ACTOR_ID, ACTIONABLE, NOW)).thenReturn(List.of());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.completeSubjectWorkflow(
                "ADMISSION_REVIEW", SUBJECT_ID, "APPROVE", "Evidence verified", ACTOR_ID));
    assertEquals(WorkflowTaskStatus.OPEN, task.getStatus());
    assertEquals(WorkflowStatus.ACTIVE, workflow.getStatus());
    verify(decisions, never()).save(any());
  }

  private void subjectWorkflowExists() {
    when(workflows
            .findFirstByWorkflowCodeAndSubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                "ADMISSION_REVIEW", SUBJECT_ID, WorkflowStatus.ACTIVE))
        .thenReturn(Optional.of(workflow));
  }

  private WorkflowTask addUserTask() {
    service.addTask(WORKFLOW_ID, userTask());
    return storedTasks.getFirst();
  }

  private CreateWorkflowTaskCommand userTask() {
    return new CreateWorkflowTaskCommand(
        "Verify evidence",
        "Check certificates",
        ACTOR_ID,
        null,
        WorkflowScopeType.INSTITUTION,
        null,
        null);
  }

  private PlatformUser user(UUID id, boolean active) {
    PlatformUser user =
        identify(
            new PlatformUser(id, "reviewer@example.test", "Reviewer", "Admissions reviewer"), id);
    if (active) user.activate();
    return user;
  }

  private <T extends AuditableEntity> T identify(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
