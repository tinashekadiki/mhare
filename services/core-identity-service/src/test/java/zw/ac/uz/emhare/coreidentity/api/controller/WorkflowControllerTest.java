package zw.ac.uz.emhare.coreidentity.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import zw.ac.uz.emhare.common.security.EmhareCurrentUser;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.coreidentity.api.model.*;
import zw.ac.uz.emhare.coreidentity.audit.CoreAuditService;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.PlatformUser;
import zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence.PlatformUserRepository;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowInstanceSummary;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowService;
import zw.ac.uz.emhare.coreidentity.workflow.WorkflowTaskSummary;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

/** Release 1 workflow audit-boundary regressions. @author Tinashe K */
@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock private WorkflowService workflowService;
    @Mock private EmhareCurrentUserResolver currentUserResolver;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private CoreAuditService auditService;
    @Mock private Authentication authentication;

    private WorkflowController controller;
    private UUID actorId;
    private UUID workflowId;
    private UUID taskId;
    private WorkflowTaskSummary task;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        EmhareCurrentUser currentUser = new EmhareCurrentUser(UUID.randomUUID(), actorId, "admin@uz.ac.zw", "admin", "Admin", Set.of());
        PlatformUser actor = mock(PlatformUser.class);
        when(actor.getId()).thenReturn(actorId);
        when(currentUserResolver.fromAuthentication(authentication)).thenReturn(Optional.of(currentUser));
        when(platformUserRepository.findById(actorId)).thenReturn(Optional.of(actor));
        controller = new WorkflowController(workflowService, currentUserResolver, platformUserRepository, auditService);
        task = new WorkflowTaskSummary(taskId, workflowId, "APPLICATION_REVIEW", "APPLICATION", UUID.randomUUID(), "APP-1", "TASK-1", "Review", "Review case", WorkflowAssigneeType.USER, actorId, "Admin", null, null, WorkflowScopeType.INSTITUTION, null, WorkflowTaskStatus.OPEN, Instant.now(), null, null, null, null, null, null, 0, List.of());
    }

    @Test
    void createAddClaimAndDecide_shouldRecordAuditEvidence() {
        WorkflowInstanceSummary workflow = new WorkflowInstanceSummary(workflowId, "APPLICATION_REVIEW", "APPLICATION", UUID.randomUUID(), "APP-1", "Review application", WorkflowStatus.ACTIVE, actorId, Instant.now(), null, 0, List.of(task));
        when(workflowService.createWorkflow(any(), any())).thenReturn(workflow);
        when(workflowService.addTask(any(), any())).thenReturn(task);
        when(workflowService.listAllTasks()).thenReturn(List.of(task));
        when(workflowService.claimTask(any(), any(Long.class), any())).thenReturn(task);
        when(workflowService.decideTask(any(), any(), any())).thenReturn(task);

        assertThat(controller.createWorkflow(authentication, new CreateWorkflowRequest("APPLICATION_REVIEW", "APPLICATION", UUID.randomUUID(), "APP-1", "Review application", "Review", "Review case", actorId, null, WorkflowScopeType.INSTITUTION, null, Instant.now()))).isSameAs(workflow);
        assertThat(controller.addTask(authentication, workflowId, new CreateWorkflowTaskRequest("Review", "Review case", actorId, null, WorkflowScopeType.INSTITUTION, null, Instant.now()))).isSameAs(task);
        assertThat(controller.claimTask(authentication, taskId, new WorkflowTaskVersionRequest(0))).isSameAs(task);
        assertThat(controller.decideTask(authentication, taskId, new WorkflowTaskDecisionRequest(0, "COMPLETED", "Evidence reviewed"))).isSameAs(task);

        verify(auditService, org.mockito.Mockito.times(4)).record(any(), any(), any(), any(), any(), any(), any());
    }
}
