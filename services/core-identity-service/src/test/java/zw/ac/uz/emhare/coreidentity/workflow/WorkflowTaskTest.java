package zw.ac.uz.emhare.coreidentity.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.coreidentity.rbac.PlatformUser;

/** @author Tinashe K */
class WorkflowTaskTest {

    @Test
    void requiresClaimOwnershipAndOptimisticVersionBeforeDecision() {
        PlatformUser initiator = activeUser("initiator@example.test");
        PlatformUser operator = activeUser("operator@example.test");
        PlatformUser otherOperator = activeUser("other@example.test");
        WorkflowInstance workflow = new WorkflowInstance(
                "ADMISSION_REVIEW",
                "APPLICATION",
                UUID.randomUUID(),
                "APP-2027-001",
                "Review admission application",
                initiator.getId(),
                Instant.parse("2027-01-10T08:00:00Z"));
        WorkflowTask task = new WorkflowTask(
                workflow,
                "WT-20270110-00000001",
                "Verify application",
                "Confirm identity and qualification evidence.",
                operator,
                null,
                WorkflowScopeType.INSTITUTION,
                null,
                Instant.parse("2027-01-11T08:00:00Z"));

        assertThrows(
                IllegalStateException.class,
                () -> task.claim(operator, Instant.parse("2027-01-10T09:00:00Z"), 1));

        task.claim(operator, Instant.parse("2027-01-10T09:00:00Z"), 0);

        IllegalStateException ownershipError = assertThrows(
                IllegalStateException.class,
                () -> task.complete(otherOperator, Instant.parse("2027-01-10T10:00:00Z"), 0));
        assertEquals("Only the operator who claimed the task can record its decision.", ownershipError.getMessage());

        task.complete(operator, Instant.parse("2027-01-10T10:00:00Z"), 0);
        assertEquals(WorkflowTaskStatus.COMPLETED, task.getStatus());
        assertEquals(operator.getId(), task.getCompletedByUser().getId());
    }

    @Test
    void requiresExactlyOneAssigneeAndAConsistentScope() {
        PlatformUser user = activeUser("operator@example.test");
        WorkflowInstance workflow = new WorkflowInstance(
                "RESULT_APPROVAL", "RESULT_BATCH", UUID.randomUUID(), "RB-001",
                "Approve result batch", user.getId(), Instant.now());

        assertThrows(IllegalArgumentException.class, () -> new WorkflowTask(
                workflow, "WT-1", "Approve", "Review evidence", null, null,
                WorkflowScopeType.INSTITUTION, null, null));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowTask(
                workflow, "WT-2", "Approve", "Review evidence", user, null,
                WorkflowScopeType.ACADEMIC_UNIT, null, null));
    }

    private PlatformUser activeUser(String email) {
        PlatformUser user = new PlatformUser(UUID.randomUUID(), email, email, email);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.activate();
        return user;
    }
}
