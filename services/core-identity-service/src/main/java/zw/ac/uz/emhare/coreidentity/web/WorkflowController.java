package zw.ac.uz.emhare.coreidentity.web;

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
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.workflow.CreateWorkflowCommand;
import zw.ac.uz.emhare.coreidentity.workflow.CreateWorkflowTaskCommand;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowDecisionCommand;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowInstanceSummary;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowTaskSummary;

/** @author Tinashe K */
@RestController
@RequestMapping("/api/core/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final EmhareCurrentUserResolver currentUserResolver;
    private final PlatformUserRepository platformUserRepository;

    public WorkflowController(
            WorkflowService workflowService,
            EmhareCurrentUserResolver currentUserResolver,
            PlatformUserRepository platformUserRepository) {
        this.workflowService = workflowService;
        this.currentUserResolver = currentUserResolver;
        this.platformUserRepository = platformUserRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public WorkflowInstanceSummary createWorkflow(
            Authentication authentication,
            @Valid @RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(new CreateWorkflowCommand(
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
                request.dueAt()), actorUserId(authentication));
    }

    @PostMapping("/{workflowInstanceId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_MANAGE')")
    public WorkflowTaskSummary addTask(
            @PathVariable UUID workflowInstanceId,
            @Valid @RequestBody CreateWorkflowTaskRequest request) {
        return workflowService.addTask(workflowInstanceId, new CreateWorkflowTaskCommand(
                request.title(),
                request.description(),
                request.assignedUserId(),
                request.assignedRoleId(),
                request.scopeType(),
                request.academicUnitId(),
                request.dueAt()));
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
        return workflowService.claimTask(workflowTaskId, request.expectedVersion(), actorUserId(authentication));
    }

    @PostMapping("/tasks/{workflowTaskId}/decision")
    @PreAuthorize("@coreRbac.has(authentication, 'CORE_WORKFLOW_TASK')")
    public WorkflowTaskSummary decideTask(
            Authentication authentication,
            @PathVariable UUID workflowTaskId,
            @Valid @RequestBody WorkflowTaskDecisionRequest request) {
        return workflowService.decideTask(
                workflowTaskId,
                new WorkflowDecisionCommand(request.expectedVersion(), request.decisionCode(), request.comment()),
                actorUserId(authentication));
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
