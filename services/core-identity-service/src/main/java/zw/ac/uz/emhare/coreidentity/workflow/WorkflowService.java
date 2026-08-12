package zw.ac.uz.emhare.coreidentity.workflow;

import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.RoleRepository;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.UserRoleAssignmentRepository;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowAssigneeType;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowDecision;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowInstance;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowScopeType;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowStatus;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowTask;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.WorkflowTaskStatus;
import zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence.WorkflowDecisionRepository;
import zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence.WorkflowInstanceRepository;
import zw.ac.uz.emhare.coreidentity.workflow.infrastructure.persistence.WorkflowTaskRepository;

import zw.ac.uz.emhare.coreidentity.workflow.application.command.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.coreidentity.integration.CoreIdentityIntegrationOutboxService;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.Role;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.UserStatus;

/** @author Tinashe K */
@Service
public class WorkflowService {

    private static final List<WorkflowTaskStatus> ACTIONABLE_STATUSES =
            List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED);
    private static final DateTimeFormatter TASK_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final WorkflowDecisionRepository workflowDecisionRepository;
    private final PlatformUserRepository platformUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final CoreIdentityIntegrationOutboxService integrationOutboxService;
    private final Clock clock;

    public WorkflowService(
            WorkflowInstanceRepository workflowInstanceRepository,
            WorkflowTaskRepository workflowTaskRepository,
            WorkflowDecisionRepository workflowDecisionRepository,
            PlatformUserRepository platformUserRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            CoreIdentityIntegrationOutboxService integrationOutboxService,
            Clock clock) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.workflowTaskRepository = workflowTaskRepository;
        this.workflowDecisionRepository = workflowDecisionRepository;
        this.platformUserRepository = platformUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.integrationOutboxService = integrationOutboxService;
        this.clock = clock;
    }

    @Transactional
    public WorkflowInstanceSummary createWorkflow(CreateWorkflowCommand command, UUID actorUserId) {
        PlatformUser actor = requireActiveUser(actorUserId);
        Instant now = clock.instant();
        WorkflowInstance workflow = workflowInstanceRepository.saveAndFlush(new WorkflowInstance(
                command.workflowCode(),
                command.subjectType(),
                command.subjectId(),
                command.subjectReference(),
                command.title(),
                actor.getId(),
                now));
        createTask(workflow, new CreateWorkflowTaskCommand(
                command.taskTitle(),
                command.taskDescription(),
                command.assignedUserId(),
                command.assignedRoleId(),
                command.scopeType(),
                command.academicUnitId(),
                command.dueAt()), now);
        return summary(workflow);
    }

    @Transactional
    public WorkflowTaskSummary addTask(
            UUID workflowInstanceId,
            CreateWorkflowTaskCommand command) {
        WorkflowInstance workflow = workflowInstanceRepository.findByIdAndDeletedAtIsNull(workflowInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance was not found."));
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Tasks can only be added to an active workflow.");
        }
        WorkflowTask task = createTask(workflow, command, clock.instant());
        return summary(task);
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskSummary> listMyQueue(UUID actorUserId) {
        requireActiveUser(actorUserId);
        return workflowTaskRepository.findAccessibleQueue(actorUserId, ACTIONABLE_STATUSES, clock.instant()).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskSummary> listAllTasks() {
        return workflowTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowInstanceSummary getWorkflow(UUID workflowInstanceId) {
        WorkflowInstance workflow = workflowInstanceRepository.findByIdAndDeletedAtIsNull(workflowInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance was not found."));
        return summary(workflow);
    }

    @Transactional
    public WorkflowTaskSummary claimTask(
            UUID workflowTaskId,
            long expectedVersion,
            UUID actorUserId) {
        PlatformUser actor = requireActiveUser(actorUserId);
        WorkflowTask task = requireAccessibleTask(workflowTaskId, actorUserId);
        task.claim(actor, clock.instant(), expectedVersion);
        workflowTaskRepository.saveAndFlush(task);
        return summary(task);
    }

    @Transactional
    public WorkflowTaskSummary decideTask(
            UUID workflowTaskId,
            WorkflowDecisionCommand command,
            UUID actorUserId) {
        PlatformUser actor = requireActiveUser(actorUserId);
        WorkflowTask task = requireAccessibleTask(workflowTaskId, actorUserId);
        Instant now = clock.instant();
        task.complete(actor, now, command.expectedVersion());
        workflowTaskRepository.saveAndFlush(task);
        workflowDecisionRepository.save(new WorkflowDecision(
                task,
                command.decisionCode(),
                command.comment(),
                actor,
                now));
        if (!workflowTaskRepository.existsByWorkflowInstanceIdAndStatusIn(
                task.getWorkflowInstance().getId(), ACTIONABLE_STATUSES)) {
            task.getWorkflowInstance().complete(now);
            workflowInstanceRepository.save(task.getWorkflowInstance());
        }
        return summary(task);
    }

    @Transactional
    public void completeSubjectWorkflow(
            String workflowCode, UUID subjectId, String decisionCode, String comment, UUID actorUserId) {
        PlatformUser actor = requireActiveUser(actorUserId);
        WorkflowInstance workflow = workflowInstanceRepository
                .findFirstByWorkflowCodeAndSubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        workflowCode, subjectId, WorkflowStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active subject workflow was not found."));
        WorkflowTask task = requireAccessibleTask(
                workflowTaskRepository.findAllByWorkflowInstanceIdOrderByCreatedAtAsc(workflow.getId()).stream()
                        .filter(candidate -> ACTIONABLE_STATUSES.contains(candidate.getStatus()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Subject workflow has no active task."))
                        .getId(), actorUserId);
        Instant now = clock.instant();
        if (task.getStatus() == WorkflowTaskStatus.OPEN) {
            task.claim(actor, now, task.getVersion());
            workflowTaskRepository.saveAndFlush(task);
        }
        task.complete(actor, now, task.getVersion());
        workflowTaskRepository.saveAndFlush(task);
        workflowDecisionRepository.save(new WorkflowDecision(task, decisionCode, comment, actor, now));
        workflow.complete(now);
        workflowInstanceRepository.save(workflow);
    }

    private WorkflowTask createTask(
            WorkflowInstance workflow,
            CreateWorkflowTaskCommand command,
            Instant now) {
        PlatformUser assignedUser = command.assignedUserId() == null
                ? null
                : requireActiveUser(command.assignedUserId());
        Role assignedRole = command.assignedRoleId() == null
                ? null
                : roleRepository.findById(command.assignedRoleId())
                        .filter(role -> !role.isDeleted())
                        .orElseThrow(() -> new IllegalArgumentException("Assigned role was not found."));
        WorkflowTask task = workflowTaskRepository.saveAndFlush(new WorkflowTask(
                workflow,
                nextTaskReference(now),
                command.title(),
                command.description(),
                assignedUser,
                assignedRole,
                command.scopeType(),
                command.academicUnitId(),
                command.dueAt()));
        List<PlatformUser> recipients = resolveRecipients(task, now);
        integrationOutboxService.enqueueWorkflowTaskNotifications(task, recipients);
        return task;
    }

    private List<PlatformUser> resolveRecipients(WorkflowTask task, Instant now) {
        if (task.getAssigneeType() == WorkflowAssigneeType.USER) {
            return List.of(task.getAssignedUser());
        }
        LinkedHashMap<UUID, PlatformUser> recipients = new LinkedHashMap<>();
        UUID academicUnitId = task.getScopeType() == WorkflowScopeType.ACADEMIC_UNIT
                ? task.getAcademicUnitId()
                : null;
        userRoleAssignmentRepository.findActiveRecipientsForRole(
                        task.getAssignedRole().getId(), academicUnitId, now)
                .forEach(assignment -> {
                    if (assignment.getUser().getStatus() == UserStatus.ACTIVE) {
                        recipients.putIfAbsent(assignment.getUser().getId(), assignment.getUser());
                    }
                });
        return List.copyOf(recipients.values());
    }

    private WorkflowTask requireAccessibleTask(UUID workflowTaskId, UUID actorUserId) {
        return workflowTaskRepository.findAccessibleQueue(actorUserId, ACTIONABLE_STATUSES, clock.instant()).stream()
                .filter(task -> task.getId().equals(workflowTaskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Workflow task was not found in the authorised queue."));
    }

    private PlatformUser requireActiveUser(UUID userId) {
        PlatformUser user = platformUserRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Workflow user was not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Workflow actions require an active user.");
        }
        return user;
    }

    private WorkflowInstanceSummary summary(WorkflowInstance workflow) {
        return new WorkflowInstanceSummary(
                workflow.getId(),
                workflow.getWorkflowCode(),
                workflow.getSubjectType(),
                workflow.getSubjectId(),
                workflow.getSubjectReference(),
                workflow.getTitle(),
                workflow.getStatus(),
                workflow.getInitiatedByUserId(),
                workflow.getInitiatedAt(),
                workflow.getCompletedAt(),
                workflow.getVersion(),
                workflowTaskRepository.findAllByWorkflowInstanceIdOrderByCreatedAtAsc(workflow.getId()).stream()
                        .map(this::summary)
                        .toList());
    }

    private WorkflowTaskSummary summary(WorkflowTask task) {
        return WorkflowTaskSummary.from(
                task,
                workflowDecisionRepository.findAllByWorkflowTaskIdOrderByDecidedAtAsc(task.getId()));
    }

    private String nextTaskReference(Instant now) {
        return "WT-" + TASK_DATE.format(now) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
