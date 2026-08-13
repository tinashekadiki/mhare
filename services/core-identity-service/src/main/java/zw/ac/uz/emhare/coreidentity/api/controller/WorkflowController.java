package zw.ac.uz.emhare.coreidentity.api.controller;

import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;

import zw.ac.uz.emhare.coreidentity.workflow.application.command.*;

import zw.ac.uz.emhare.coreidentity.api.model.*;

import zw.ac.uz.emhare.coreidentity.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.CreateWorkflowTaskCommand;
import zw.ac.uz.emhare.coreidentity.workflow.application.command.WorkflowDecisionCommand;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowInstanceSummary;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowTaskSummary;
import zw.ac.uz.emhare.coreidentity.audit.CoreAuditService;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/core/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final EmhareCurrentUserResolver currentUserResolver;
    private final PlatformUserRepository platformUserRepository;
    private final CoreAuditService coreAuditService;

    public WorkflowController(
            WorkflowService workflowService,
            EmhareCurrentUserResolver currentUserResolver,
            PlatformUserRepository platformUserRepository,
            CoreAuditService coreAuditService) {
        this.workflowService = workflowService;
        this.currentUserResolver = currentUserResolver;
        this.platformUserRepository = platformUserRepository;
        this.coreAuditService = coreAuditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public WorkflowInstanceSummary createWorkflow(
            Authentication authentication,
            @Valid @RequestBody CreateWorkflowRequest request) {
        UUID actorUserId = actorUserId(authentication);
        WorkflowInstanceSummary result = workflowService.createWorkflow(new CreateWorkflowCommand(
                request.workflowCode(),
                request.subjectType(),
                request.subjectId(),
                request.subjectReference(),
                request.title(),
                request.taskTitle(),
                request.taskDescription(),
                request.assignedUserId(),
                request.assignedRoleId(),
                request.scopeType(),
                request.academicUnitId(),
                request.dueAt()), actorUserId);
        coreAuditService.record(actorUserId, "CORE_WORKFLOW_CREATED", "WORKFLOW_INSTANCE", result.id(),
                "Created workflow " + result.workflowCode() + ".", null, result);
        return result;
    }

    @PostMapping("/{workflowInstanceId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public WorkflowTaskSummary addTask(
            Authentication authentication,
            @PathVariable UUID workflowInstanceId,
            @Valid @RequestBody CreateWorkflowTaskRequest request) {
        WorkflowTaskSummary result = workflowService.addTask(workflowInstanceId, new CreateWorkflowTaskCommand(
                request.title(),
                request.description(),
                request.assignedUserId(),
                request.assignedRoleId(),
                request.scopeType(),
                request.academicUnitId(),
                request.dueAt()));
        coreAuditService.record(actorUserId(authentication), "CORE_WORKFLOW_TASK_ADDED", "WORKFLOW_TASK", result.id(),
                "Added workflow task " + result.taskReference() + ".", null, result);
        return result;
    }

    @GetMapping("/{workflowInstanceId}")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public WorkflowInstanceSummary getWorkflow(@PathVariable UUID workflowInstanceId) {
        return workflowService.getWorkflow(workflowInstanceId);
    }

    @GetMapping("/tasks")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public List<WorkflowTaskSummary> listAllTasks() {
        return workflowService.listAllTasks();
    }

    @GetMapping("/tasks/mine")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_TASK')")
    public List<WorkflowTaskSummary> listMyQueue(Authentication authentication) {
        return workflowService.listMyQueue(actorUserId(authentication));
    }

    @PostMapping("/tasks/{workflowTaskId}/claim")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_TASK')")
    public WorkflowTaskSummary claimTask(
            Authentication authentication,
            @PathVariable UUID workflowTaskId,
            @Valid @RequestBody WorkflowTaskVersionRequest request) {
        UUID actorUserId = actorUserId(authentication);
        WorkflowTaskSummary before = workflowService.listAllTasks().stream()
                .filter(task -> task.id().equals(workflowTaskId)).findFirst().orElse(null);
        WorkflowTaskSummary result = workflowService.claimTask(workflowTaskId, request.expectedVersion(), actorUserId);
        coreAuditService.record(actorUserId, "CORE_WORKFLOW_TASK_CLAIMED", "WORKFLOW_TASK", workflowTaskId,
                "Claimed workflow task " + result.taskReference() + ".", before, result);
        return result;
    }

    @PostMapping("/tasks/{workflowTaskId}/decision")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_TASK')")
    public WorkflowTaskSummary decideTask(
            Authentication authentication,
            @PathVariable UUID workflowTaskId,
            @Valid @RequestBody WorkflowTaskDecisionRequest request) {
        UUID actorUserId = actorUserId(authentication);
        WorkflowTaskSummary before = workflowService.listAllTasks().stream()
                .filter(task -> task.id().equals(workflowTaskId)).findFirst().orElse(null);
        WorkflowTaskSummary result = workflowService.decideTask(
                workflowTaskId,
                new WorkflowDecisionCommand(request.expectedVersion(), request.decisionCode(), request.comment()),
                actorUserId);
        coreAuditService.record(actorUserId, "CORE_WORKFLOW_TASK_DECIDED", "WORKFLOW_TASK", workflowTaskId,
                "Recorded workflow decision " + request.decisionCode() + ".", before, result);
        return result;
    }

    private UUID actorUserId(Authentication authentication) {
        EmhareCurrentUser currentUser = currentUserResolver.fromAuthentication(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is required."));
        if (currentUser.localUserId() != null) {
            return platformUserRepository.findById(currentUser.localUserId())
                    .map(PlatformUser::getId)
                    .orElseGet(() -> resolveIdentityProviderUser(currentUser));
        }
        return resolveIdentityProviderUser(currentUser);
    }

    private UUID resolveIdentityProviderUser(EmhareCurrentUser currentUser) {
        if (currentUser.keycloakUserId() != null) {
            return platformUserRepository.findByKeycloakUserId(currentUser.keycloakUserId())
                    .map(PlatformUser::getId)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user has not been synchronised to Core Identity."));
        }
        if (currentUser.email() != null) {
            return platformUserRepository.findByEmail(currentUser.email())
                    .map(PlatformUser::getId)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user has not been synchronised to Core Identity."));
        }
        throw new IllegalStateException("Authenticated user has no resolvable Core Identity record.");
    }
}
